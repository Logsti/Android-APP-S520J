#!/usr/bin/env node
/**
 * 生成 cloudbaserc.json（含服务端专用环境变量）。
 * 凭据只从本机 local.properties 读取；生成的 cloudbaserc.json 已被 .gitignore 排除。
 * 用法：node cloudbase/scripts/gen-cloudbaserc.js
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

const groups = ['auth', 'space', 'content', 'sync', 'media', 'recycle', 'notification', 'settings', 'backup'];

const config = {
  $schema: 'https://static.cloudbase.net/cli/cloudbaserc.schema.json',
  envId,
  functionRoot: 'cloudbase/functions',
  functions: groups.map((name) => ({
    name,
    timeout: 20,
    runtime: 'Nodejs20.19',
    handler: 'index.main',
    // CB_API_KEY 仅用于云函数 → gateway exec-pgsql，禁止下发客户端。
    envVariables: {
      CB_ENV_ID: envId,
      CB_API_KEY: apiKey,
      CB_PG_ROLE: 'cloudbase_postgres',
    },
  })),
  // HTTP 访问服务：不指定 domain 时使用环境默认域名，客户端据此直连。
  // enableAuth=false：由云函数内部校验自有 access token，不依赖云开发登录态。
  gateway: {
    routes: groups.map((name) => ({
      path: `/api/${name}`,
      target: `function:${name}`,
    })),
  },
};

fs.writeFileSync(path.join(root, 'cloudbaserc.json'), JSON.stringify(config, null, 2) + '\n');
console.log(`wrote cloudbaserc.json (${groups.length} functions)`);
