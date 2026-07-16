#!/usr/bin/env python3
"""Manually verify an Open-Meteo Weather Forecast API response.

No third-party packages are required.

Examples:
  python verify_openmeteo.py
  python verify_openmeteo.py --latitude 28.6139 --longitude 77.2090 --timezone Asia/Kolkata
  python verify_openmeteo.py --hourly temperature_2m,precipitation,wind_speed_10m --save response.json
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


ENDPOINT = "https://api.open-meteo.com/v1/forecast"
DEFAULT_HOURLY = "temperature_2m,relative_humidity_2m,precipitation,weather_code,wind_speed_10m"
DEFAULT_DAILY = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum"
DEFAULT_CURRENT = "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Call and validate Open-Meteo /v1/forecast.")
    parser.add_argument("--latitude", type=float, default=28.6139, help="WGS84 latitude (default: New Delhi).")
    parser.add_argument("--longitude", type=float, default=77.2090, help="WGS84 longitude (default: New Delhi).")
    parser.add_argument("--timezone", default="Asia/Kolkata", help="IANA timezone or 'auto'.")
    parser.add_argument("--forecast-days", type=int, choices=range(1, 17), default=3, help="Days to request (1–16).")
    parser.add_argument("--hourly", default=DEFAULT_HOURLY, help="Comma-separated hourly fields; empty string disables.")
    parser.add_argument("--daily", default=DEFAULT_DAILY, help="Comma-separated daily fields; empty string disables.")
    parser.add_argument("--current", default=DEFAULT_CURRENT, help="Comma-separated current fields; empty string disables.")
    parser.add_argument("--models", help="Optional model selector, e.g. ecmwf_ifs025.")
    parser.add_argument("--save", type=Path, help="Write the complete JSON response to this path.")
    return parser.parse_args()


def fetch(params: dict[str, object]) -> tuple[str, object]:
    url = f"{ENDPOINT}?{urlencode(params)}"
    request = Request(url, headers={"Accept": "application/json", "User-Agent": "openmeteo-manual-verifier/1.0"})
    try:
        with urlopen(request, timeout=20) as response:
            return url, json.load(response)
    except HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} {exc.reason}: {detail}") from exc
    except URLError as exc:
        raise RuntimeError(f"Network error: {exc.reason}") from exc


def validate_payload(payload: object, requested: dict[str, str]) -> list[str]:
    issues: list[str] = []
    records = payload if isinstance(payload, list) else [payload]
    if not all(isinstance(record, dict) for record in records):
        return ["Response is neither an object nor a list of objects."]
    for index, record in enumerate(records):
        prefix = f"location {index}: " if len(records) > 1 else ""
        if record.get("error"):
            issues.append(prefix + f"API error: {record.get('reason', 'unknown reason')}")
            continue
        for key in ("latitude", "longitude", "timezone", "utc_offset_seconds"):
            if key not in record:
                issues.append(prefix + f"missing metadata field '{key}'.")
        for section in ("hourly", "daily"):
            fields = [value for value in requested.get(section, "").split(",") if value]
            if not fields:
                continue
            values = record.get(section)
            units = record.get(f"{section}_units")
            if not isinstance(values, dict) or not isinstance(units, dict):
                issues.append(prefix + f"missing '{section}' values or '{section}_units'.")
                continue
            times = values.get("time")
            if not isinstance(times, list):
                issues.append(prefix + f"'{section}.time' is not an array.")
                continue
            for field in fields:
                series = values.get(field)
                if not isinstance(series, list):
                    issues.append(prefix + f"missing array '{section}.{field}'.")
                elif len(series) != len(times):
                    issues.append(prefix + f"'{section}.{field}' has {len(series)} values; time has {len(times)}.")
                if field not in units:
                    issues.append(prefix + f"missing unit for '{section}.{field}'.")
        for field in [value for value in requested.get("current", "").split(",") if value]:
            if field not in record.get("current", {}):
                issues.append(prefix + f"missing current value '{field}'.")
    return issues


def main() -> int:
    args = parse_args()
    params: dict[str, object] = {
        "latitude": args.latitude,
        "longitude": args.longitude,
        "timezone": args.timezone,
        "forecast_days": args.forecast_days,
    }
    requested = {"hourly": args.hourly, "daily": args.daily, "current": args.current}
    for key, value in requested.items():
        if value:
            params[key] = value
    if args.models:
        params["models"] = args.models

    try:
        url, payload = fetch(params)
    except RuntimeError as exc:
        print(f"Request failed: {exc}", file=sys.stderr)
        return 1

    print("Request URL:\n" + url)
    print("\nResponse summary:")
    for key in ("latitude", "longitude", "elevation", "timezone", "timezone_abbreviation", "utc_offset_seconds", "generationtime_ms"):
        if isinstance(payload, dict) and key in payload:
            print(f"  {key}: {payload[key]}")
    if isinstance(payload, dict):
        for section in ("current", "hourly", "daily"):
            value = payload.get(section)
            if isinstance(value, dict):
                count = len(value.get("time", [])) if section != "current" else 1
                print(f"  {section}: {count} record(s); fields: {', '.join(value.keys())}")

    issues = validate_payload(payload, requested)
    print("\nValidation: " + ("PASS — requested series align with their time arrays." if not issues else "FAIL"))
    for issue in issues:
        print("  - " + issue)

    if args.save:
        args.save.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"\nSaved complete response to: {args.save.resolve()}")
    return 0 if not issues else 2


if __name__ == "__main__":
    raise SystemExit(main())
