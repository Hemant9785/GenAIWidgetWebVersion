const fs = require('fs');
const path = require('path');

const srcDir = 'D:\\widget\\v1\\widget\\gen-ui-forge-rendering-assets\\src\\main\\res\\drawable';
const destDir = 'D:\\widget\\v2\\AndroidApp\\app\\src\\main\\res\\drawable';

if (!fs.existsSync(srcDir)) {
  console.error(`Source directory not found: ${srcDir}`);
  process.exit(1);
}

if (!fs.existsSync(destDir)) {
  fs.mkdirSync(destDir, { recursive: true });
}

const files = fs.readdirSync(srcDir);
let count = 0;

files.forEach(file => {
  const srcFile = path.join(srcDir, file);
  const destFile = path.join(destDir, file);
  
  if (fs.statSync(srcFile).isFile()) {
    fs.copyFileSync(srcFile, destFile);
    count++;
  }
});

console.log(`Successfully copied ${count} drawable assets to ${destDir}`);
