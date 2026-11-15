#!/usr/bin/env node
/**
 * 一次性初始化：为云函数创建专用 PG 登录角色，并把连接参数写入 local.properties（已 gitignore）。
 * 角色具备 BYPASSRLS，仅用于云函数服务端；RLS 拒绝策略仍对 anon / authenticated 生效。
 *
 * 用法：node cloudbase/scripts/init-pg-role.js
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const root = path.resolve(__dirname, '..', '..');
const propsPath = path.join(root, 'local.properties');
const props = fs.readFileSync(propsPath, 'utf8');
const readProp = (key) => {
  const line = props.split(/\r?\n/).find((l) => l.startsWith(key + '='));
  return line ? line.slice(key.length + 1).trim() : '';
};
const apiKey = readProp('cloudbase.apiKey');
const envId = readProp('cloudbase.envId');
if (!apiKey || !envId) throw new Error('local.properties 缺少 cloudbase.apiKey / cloudbase.envId');

const host = '30.251.13.209';
const port = '50975';
const database = 'postgres-nemdgrxi';
const role = 's520j_app';
const password = crypto.randomBytes(24).toString('hex');

async function exec(sql) {
  const res = await fetch(`https://${envId}.api.tcloudbasegateway.com/v1/rdb/exec-pgsql`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ sql, role: 'cloudbase_postgres' }),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${sql.slice(0, 60)} -> ${text}`);
  return text;
}

(async () => {
  const statements = [
    `CREATE ROLE ${role} LOGIN PASSWORD '${password}'`,
    `ALTER ROLE ${role} BYPASSRLS`,
    `GRANT CONNECT ON DATABASE "${database}" TO ${role}`,
    `GRANT USAGE ON SCHEMA public TO ${role}`,
    `GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ${role}`,
    `GRANT EXECUTE ON FUNCTION s520j_is_space_member(text, text) TO ${role}`,
  ];
  for (const sql of statements) {
    await exec(sql);
    console.log('ok:', sql.slice(0, 62));
  }

  const lines = [
    `cloudbase.pgHost=${host}`,
    `cloudbase.pgPort=${port}`,
    `cloudbase.pgDatabase=${database}`,
    `cloudbase.pgUser=${role}`,
    `cloudbase.pgPassword=${password}`,
    '',
  ].join('\n');
  const existing = props.split(/\r?\n/).filter((l) => !l.startsWith('cloudbase.pg'));
  fs.writeFileSync(propsPath, existing.join('\n').replace(/\n+$/, '') + '\n' + lines);
  console.log('已写入 local.properties: cloudbase.pgHost / pgPort / pgDatabase / pgUser / pgPassword');
})().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
