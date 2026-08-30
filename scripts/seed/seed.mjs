#!/usr/bin/env node
// Seed demo data for kb-svc (real embedding pipeline) and order-svc (real HTTP use cases).
//
// 用法:
//   node scripts/seed/seed.mjs                 # 全量：kb + order
//   node scripts/seed/seed.mjs --kb-only       # 仅知识库
//   node scripts/seed/seed.mjs --kb-refresh    # 重传已存在的知识库文档
//   node scripts/seed/seed.mjs --order-only    # 仅订单
//   node scripts/seed/seed.mjs --smoke         # 每种订单状态只造 1 单（快速验证）
//
// 前置（见 README 快速开始）:
//   - postgres + redis 已启动，且已创建 `order` / `kb` 库
//   - kb-svc 运行在 8001（其环境需导出 DASHSCOPE_API_KEY）
//   - order-svc 运行在 8020（默认 demo profile，外部依赖全 Mock）
// 环境变量可覆盖: KB_BASE_URL / ORDER_BASE_URL。

import { execFileSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(SCRIPT_DIR, '../..');
const KB_DIR = path.resolve(SCRIPT_DIR, 'data/kb');

const KB_BASE = process.env.KB_BASE_URL ?? 'http://localhost:8001';
const ORDER_BASE = process.env.ORDER_BASE_URL ?? 'http://localhost:8020';

const args = process.argv.slice(2);
const ONLY = args.includes('--kb-only') ? 'kb' : args.includes('--order-only') ? 'order' : 'both';
const KB_REFRESH = args.includes('--kb-refresh');
const SMOKE = args.includes('--smoke');

// ─── KB manifest ────────────────────────────────────────────────────────────────
// 一类一库（README「电商客服知识库」推荐拆分）；「全局事实与边界」按 README 要求
// 始终纳入每个主题库，以消解跨库口径冲突。
const TOPICS = [
  { file: '01_店铺与商品知识库.md', name: '店铺与商品' },
  { file: '02_订单与履约知识库.md', name: '订单与履约' },
  { file: '03_物流与配送知识库.md', name: '物流与配送' },
  { file: '04_售后退换货知识库.md', name: '售后退换货' },
  { file: '05_支付与发票知识库.md', name: '支付与发票' },
  { file: '06_优惠券会员与活动知识库.md', name: '优惠券会员与活动' },
  { file: '07_账号隐私与安全知识库.md', name: '账号隐私与安全' },
  { file: '08_客服标准口径与FAQ知识库.md', name: '客服标准口径与FAQ' },
  { file: '09_售后场景SOP知识库.md', name: '售后场景SOP' },
  { file: '10_商品选购与推荐知识库.md', name: '商品选购与推荐' },
  { file: '11_企业采购与大客户知识库.md', name: '企业采购与大客户' },
];
const GLOBAL_DOC = { file: '12_知识库全局事实与边界.md', name: '知识库全局事实与边界' };

// ─── order seed ────────────────────────────────────────────────────────────────
const SKUS = ['SKU-001', 'SKU-002', 'SKU-003']; // 与 ProductCatalogClientImpl 默认目录一致
const RECIPIENTS = [
  { name: '张伟', phone: '13800000001', province: '广东省', city: '深圳市', district: '南山区', detailAddress: '科技园路 1 号 1 栋 101' },
  { name: '王芳', phone: '13800000002', province: '北京市', city: '北京市', district: '朝阳区', detailAddress: '望京街 2 号 2 栋 202' },
  { name: '李娜', phone: '13800000003', province: '上海市', city: '上海市', district: '浦东新区', detailAddress: '张江路 3 号 3 栋 303' },
  { name: '刘强', phone: '13800000004', province: '浙江省', city: '杭州市', district: '西湖区', detailAddress: '文三路 4 号 4 栋 404' },
  { name: '陈静', phone: '13800000005', province: '四川省', city: '成都市', district: '高新区', detailAddress: '天府大道 5 号 5 栋 505' },
  { name: '杨帆', phone: '13800000006', province: '江苏省', city: '南京市', district: '鼓楼区', detailAddress: '中山路 6 号 6 栋 606' },
];

// 六态覆盖，PAID/SHIPPED 居多，总量 40。
const STATE_PLAN = [
  { state: 'PENDING_PAYMENT', count: 8 },
  { state: 'PAID', count: 10 },
  { state: 'SHIPPED', count: 8 },
  { state: 'COMPLETED', count: 6 },
  { state: 'CANCELED', count: 4 },
  { state: 'REFUNDED', count: 4 },
];

// ─── HTTP helpers ──────────────────────────────────────────────────────────────
async function http(method, p, { base = ORDER_BASE, body, idempotency } = {}) {
  const headers = { 'API-Version': '1' };
  if (idempotency) headers['Idempotency-Key'] = idempotency;
  let payload;
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  const res = await fetch(base + p, { method, headers, body: payload });
  const text = await res.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }
  if (!res.ok) throw new Error(`${method} ${base}${p} -> HTTP ${res.status}\n${text}`);
  return data;
}

async function uploadDocument(kbNo, filename) {
  const buf = await readFile(path.join(KB_DIR, filename));
  const form = new FormData();
  form.append('file', new Blob([buf], { type: 'text/markdown' }), filename);
  const res = await fetch(`${KB_BASE}/kbs/${kbNo}/documents`, { method: 'POST', body: form });
  const text = await res.text();
  if (!res.ok) throw new Error(`upload ${filename} -> ${kbNo}: HTTP ${res.status}\n${text}`);
  return text ? JSON.parse(text) : {};
}

// ─── kb seed ───────────────────────────────────────────────────────────────────
async function ensureDoc(kbNo, filename) {
  const docs = await http('GET', `/kbs/${kbNo}/documents`, { base: KB_BASE });
  const existing = docs.find((d) => d.name === filename);
  if (existing && existing.status === 'READY' && !KB_REFRESH) {
    console.log(`  (skip) ${existing.documentNo} 已就绪`);
    return 0;
  }
  if (existing) {
    await http('DELETE', `/kbs/${kbNo}/documents/${existing.documentNo}`, { base: KB_BASE });
    const action = KB_REFRESH ? 'refresh' : 'retry';
    console.log(`  (${action}) 删除 ${existing.documentNo}（status=${existing.status}）后重传`);
  }
  await uploadDocument(kbNo, filename);
  console.log(`  uploaded ${filename} -> ${kbNo}`);
  return 1;
}

async function seedKb() {
  const list = await http('GET', '/kbs', { base: KB_BASE });
  const byName = new Map(list.map((kb) => [kb.name, kb]));
  const kbNoOf = async (name) => {
    if (byName.has(name)) return byName.get(name).kbNo;
    const kb = await http('POST', '/kbs', { base: KB_BASE, body: { name } });
    byName.set(name, kb);
    console.log(`  created KB "${name}" (${kb.kbNo})`);
    return kb.kbNo;
  };

  let uploaded = 0;
  for (const topic of TOPICS) {
    const kbNo = await kbNoOf(topic.name);
    uploaded += await ensureDoc(kbNo, topic.file);
    uploaded += await ensureDoc(kbNo, GLOBAL_DOC.file); // 全局事实始终纳入
  }
  const gKbNo = await kbNoOf(GLOBAL_DOC.name);
  uploaded += await ensureDoc(gKbNo, GLOBAL_DOC.file);
  return { knowledgeBases: byName.size, documentsUploaded: uploaded };
}

// ─── order seed ────────────────────────────────────────────────────────────────
function psqlStrings(sql) {
  try {
    const out = process.env.SEED_PG_HOST
      ? execFileSync(
          'psql',
          ['-h', process.env.SEED_PG_HOST, '-U', 'acm', '-d', 'order', '-tA', '-c', sql],
          { encoding: 'utf8' },
        )
      : execFileSync(
          'docker',
          ['compose', 'exec', '-T', 'postgres', 'psql', '-U', 'acm', '-d', 'order', '-tA', '-c', sql],
          { cwd: REPO_ROOT, encoding: 'utf8' },
        );
    return out
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean);
  } catch (e) {
    throw new Error(
      `psql 查询失败（发货需要 order_items.id，公共 API 未在订单详情中暴露该字段）: ${e.message}\nSQL: ${sql}`,
    );
  }
}

async function createOrder(n) {
  const lineCount = 1 + (n % 3);
  const items = [];
  for (let k = 0; k < lineCount; k++) {
    items.push({ skuId: SKUS[(n + k) % SKUS.length], quantity: 1 + ((n + k) % 3) });
  }
  const recipient = RECIPIENTS[n % RECIPIENTS.length];
  return http('POST', '/orders', {
    body: {
      customerId: `cust-${String(n).padStart(3, '0')}`,
      currency: 'CNY',
      recipient,
      items,
    },
    idempotency: `seed-ord-${n}`,
  });
}

async function pay(order, n) {
  const payment = await http('POST', `/orders/${order.orderNo}/payments`, {
    idempotency: `seed-ord-${n}-pay`,
  });
  await http('POST', `/mock/payments/${payment.paymentNo}/succeed`, {
    idempotency: `seed-ord-${n}-pay-ok`,
  });
}

async function cancel(order, n) {
  await http('POST', `/orders/${order.orderNo}/cancel`, {
    body: { reason: '用户取消未支付订单（种子数据）' },
    idempotency: `seed-ord-${n}-cancel`,
  });
}

async function refund(order, n) {
  const refund = await http('POST', `/orders/${order.orderNo}/refunds`, {
    body: { reason: '申请整单退款（种子数据）' },
    idempotency: `seed-ord-${n}-refund-req`,
  });
  await http('POST', `/admin/refunds/${refund.refundNo}/approve`, {
    body: { reviewer: 'seed-admin', comment: '审核通过（种子数据）' },
    idempotency: `seed-ord-${n}-refund-apr`,
  });
}

async function ship(order, n) {
  const lineNos = order.items.map((it) => it.lineNo).sort((a, b) => a - b);
  const ids = psqlStrings(
    `SELECT id FROM order_items WHERE order_id = '${order.id}' AND line_no IN (${lineNos.join(',')}) ORDER BY line_no`,
  );
  if (ids.length !== order.items.length) {
    throw new Error(`order ${order.orderNo}: 期望 ${order.items.length} 个 order_item id，实际 ${ids.length}`);
  }
  const items = order.items.map((it, idx) => ({ orderItemId: ids[idx], quantity: it.quantity }));
  const shipment = await http('POST', `/admin/orders/${order.orderNo}/shipments`, {
    body: { carrierCode: 'MOCK_EXPRESS', items },
    idempotency: `seed-ord-${n}-ship`,
  });
  return shipment.shipmentNo;
}

async function receive(order, shipmentNo, n) {
  await http('POST', `/orders/${order.orderNo}/shipments/${shipmentNo}/confirm-receipt`, {
    idempotency: `seed-ord-${n}-rcv`,
  });
}

async function seedOrder() {
  const plan = SMOKE ? STATE_PLAN.map((s) => ({ ...s, count: 1 })) : STATE_PLAN;
  const counts = {};
  let n = 0;
  for (const { state, count } of plan) {
    for (let i = 0; i < count; i++) {
      n++;
      const order = await createOrder(n);
      switch (state) {
        case 'PENDING_PAYMENT':
          break;
        case 'PAID':
          await pay(order, n);
          break;
        case 'CANCELED':
          await cancel(order, n);
          break;
        case 'REFUNDED':
          await pay(order, n);
          await refund(order, n);
          break;
        case 'SHIPPED':
          await pay(order, n);
          await ship(order, n);
          break;
        case 'COMPLETED':
          await pay(order, n);
          await receive(order, await ship(order, n), n);
          break;
        default:
          throw new Error(`unknown state: ${state}`);
      }
      counts[state] = (counts[state] ?? 0) + 1;
      console.log(`  order ${String(n).padStart(2, '0')}: ${order.orderNo} -> ${state}`);
    }
  }
  return counts;
}

async function waitForHealth(base, label, timeoutMs = Number(process.env.WAIT_TIMEOUT_MS) || 240000) {
  const url = `${base}/actuator/health`;
  const deadline = Date.now() + timeoutMs;
  while (true) {
    if (Date.now() >= deadline) {
      throw new Error(`${label} 未在 ${timeoutMs / 1000}s 内就绪: ${url}`);
    }
    try {
      if ((await fetch(url)).ok) {
        console.log(`  ${label} ready`);
        return;
      }
    } catch {}
    await new Promise((resolve) => setTimeout(resolve, 3000));
  }
}

// ─── main ──────────────────────────────────────────────────────────────────────
const summary = {};
async function main() {
  if (ONLY !== 'order') await waitForHealth(KB_BASE, 'kb-svc');
  if (ONLY !== 'kb') await waitForHealth(ORDER_BASE, 'order-svc');
  if (ONLY !== 'order') {
    console.log('== seed kb-svc ==');
    summary.kb = await seedKb();
  }
  if (ONLY !== 'kb') {
    console.log('== seed order-svc ==');
    summary.order = await seedOrder();
  }
  console.log('\n== done ==');
  console.log(JSON.stringify(summary, null, 2));
}

main().catch((e) => {
  console.error('\n== seed FAILED ==');
  console.error(e.message);
  process.exitCode = 1;
});
