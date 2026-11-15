#!/usr/bin/env node
/**
 * CloudBase PostgreSQL 初始化脚本执行器。
 * 用法：node cloudbase/scripts/run-sql.js cloudbase/db/001_schema.sql [more.sql ...]
 *
 * 说明：
 *  - 凭据仅从本机 local.properties 读取（该文件已被 .gitignore 排除）。
 *  - exec-pgsql 以“单条语句”的预处理方式执行，因此这里按分号拆分逐条提交。
 */

const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..', '..');
const localProps = fs.readFileSync(path.join(root, 'local.properties'), 'utf8');
const readProp = (key) => {
  const line = localProps.split(/\r?\n/).find((l) => l.startsWith(key + '='));
  return line ? line.slice(key.length + 1).trim() : '';
};

const envId = readProp('cloudbase.envId');
const apiKey = readProp('cloudbase.apiKey');
if (!envId || !apiKey) {
  console.error('缺少 cloudbase.envId / cloudbase.apiKey（local.properties）');
  process.exit(1);
}

const files = process.argv.slice(2);
if (files.length === 0) {
  console.error('请指定要执行的 .sql 文件');
  process.exit(1);
}

function splitStatements(sql) {
  return sql
    .replace(/^\uFEFF/, '')
    .split(';')
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
    .filter((s) => !/^(BEGIN|COMMIT|END)$/i.test(s.replace(/\n/g, ' ').trim()))
    .map((s) => s + ';');
}

async function exec(sql) {
  const res = await fetch(
    `https://${envId}.api.tcloudbasegateway.com/v1/rdb/exec-pgsql`,
    {
      method: 'POST',
      headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
      // 默认以只读角色执行；DDL/DML 需显式指定管理角色 cloudbase_postgres。
      body: JSON.stringify({ sql, role: 'cloudbase_postgres' }),
    }
  );
  const text = await res.text();
  if (!res.ok) throw new Error(text);
  return text;
}

(async () => {
  for (const file of files) {
    const abs = path.resolve(root, file);
    const statements = splitStatements(fs.readFileSync(abs, 'utf8'));
    console.log(`\n== ${file} (${statements.length} statements)`);
    let ok = 0;
    for (const st of statements) {
      const head = st.replace(/\s+/g, ' ').slice(0, 72);
      try {
        const result = await exec(st);
        if (/(?:^|\n)\s*SELECT\b/i.test(st)) console.log(result);
        ok += 1;
      } catch (e) {
        console.error(`FAILED: ${head}\n${e.message}`);
        process.exit(1);
      }
    }
    console.log(`done: ${ok}/${statements.length}`);
  }
})();
