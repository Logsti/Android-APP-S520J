#!/usr/bin/env node
/**
 * 云函数打包准备：
 *  1) 补齐各函数目录的 index.js 与 package.json（仅在缺失时生成，避免覆盖手改内容）；
 *  2) 把 functions/_shared 复制进每个函数目录（CloudBase 只上传函数目录内文件）。
 * 部署前执行：node cloudbase/scripts/prepare-functions.js
 */
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..', '..');
const fnRoot = path.join(root, 'cloudbase', 'functions');
const groups = ['auth', 'space', 'content', 'sync', 'media', 'recycle', 'notification', 'settings', 'backup', 'dishSync'];

// 一个函数可承载多个接口前缀（entry.js 按前缀路由）。
const actionGroups = {
  auth: ['auth', 'users', 'profile'],
  space: ['space', 'challenge', 'event'],
  content: ['content', 'dish'],
};

// 定时任务不经过 action 路由，使用独立入口。
const customEntries = {
  dishSync: "const { dishSyncInternal } = require('./_shared/dishHandlers');\n"
    + 'exports.main = async function main(event) {\n'
    + '  return dishSyncInternal(event || {});\n'
    + '};\n',
};

const pkg = (group) => ({
  name: `s520j-${group}`,
  version: '1.0.0',
  main: 'index.js',
  dependencies: {},
});

function copyDir(src, dest) {
  fs.mkdirSync(dest, { recursive: true });
  for (const entry of fs.readdirSync(src)) {
    const s = path.join(src, entry);
    const d = path.join(dest, entry);
    if (fs.statSync(s).isDirectory()) copyDir(s, d);
    else fs.copyFileSync(s, d);
  }
}

for (const group of groups) {
  const dir = path.join(fnRoot, group);
  fs.mkdirSync(dir, { recursive: true });

  const indexFile = path.join(dir, 'index.js');
  const allowed = actionGroups[group] || [group];
  // 总是覆盖：入口前缀属于部署契约，避免手改后与 handlers 路由不一致。
  fs.writeFileSync(
    indexFile,
    customEntries[group]
      || `const { createEntry } = require('./_shared/entry');\nexports.main = createEntry(${JSON.stringify(allowed)});\n`
  );

  const pkgFile = path.join(dir, 'package.json');
  fs.writeFileSync(pkgFile, JSON.stringify(pkg(group), null, 2) + '\n');

  const target = path.join(dir, '_shared');
  fs.rmSync(target, { recursive: true, force: true });
  copyDir(path.join(fnRoot, '_shared'), target);
}

console.log(`prepared ${groups.length} functions`);
