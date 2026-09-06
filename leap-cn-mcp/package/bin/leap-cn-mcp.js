#!/usr/bin/env node
import { createInterface } from 'node:readline';
import { readFileSync } from 'node:fs';
import { createClientFromEnv, commandPresets } from '../src/leapmotor-cn-sdk.js';

const packageInfo = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'));

const client = createClientFromEnv({
  sessionFile: process.env.LEAP_SESSION_FILE || new URL('../.leap-session.json', import.meta.url).pathname,
  allowWrite: process.env.LEAP_ALLOW_WRITE === '1'
});
const defaultOperationPassword = process.env.LEAP_OPERATION_PASSWORD || '';
const operationPasswordProperty = defaultOperationPassword
  ? {}
  : { operationPassword: stringSchema('Operation password/PIN; encrypted into oppwd') };

const tools = [
  tool('leap_session', 'Read current Leapmotor session summary.', {}),
  tool('leap_refresh_token', 'Refresh the gateway access token immediately. Normal authenticated tools also refresh automatically.', {}),
  tool('leap_refresh_old_token', 'Refresh the old App token used by remote-control/mileage signatures via getnewtoken. Requires phone in session or phone argument. Control tools also refresh automatically near expiry.', {
    phone: stringSchema('Phone number if not already stored in session from login')
  }),
  tool('leap_send_sms', 'Send SMS login code. Phone is RSA encrypted before sending.', {
    phone: stringSchema('Phone number')
  }, ['phone']),
  tool('leap_login_sms', 'Verify SMS code and exchange gateway token. Stores phone for old-token auto-refresh.', {
    phone: stringSchema('Phone number'),
    smsCode: stringSchema('SMS verification code')
  }, ['phone', 'smsCode']),
  tool('leap_vehicles', 'List vehicles bound to the account.', {}),
  tool('leap_select_vehicle', 'Select active vehicle for subsequent calls.', {
    vin: stringSchema('Vehicle VIN'),
    carType: stringSchema('Optional car type')
  }, ['vin']),
  tool('leap_route', 'Fetch selected vehicle route.', {}),
  tool('leap_state', 'Fetch realtime state, normalized summary, and fieldDefinitions for every raw signalMap field. Always use fieldDefinitions for meanings, units, enums, and confidence; do not infer semantics for fields marked unknown.', {}),
  tool('leap_mileage', 'Fetch selected vehicle mileage and energy detail.', {}),
  tool('leap_location', 'Fetch old chassis/location endpoint. Realtime state may have better coordinates.', {}),
  tool('leap_overview', 'Fetch route, realtime state, mileage, and location together.', {}),
  tool('leap_commands', 'List supported remote-control command presets.', {}),
  tool('leap_control_preview', 'Build a remote-control request without sending it.', {
    command: stringSchema('Command preset name'),
    windowPosition: integerSchema('S01 front-window position from 0 (maximum open) to 10 (closed); 8 is vent. Used with command=window.'),
    value: stringSchema('Legacy custom value for value-based commands; windowPosition takes priority'),
    cmdid: stringSchema('Custom command id'),
    stateJson: stringSchema('Custom state JSON'),
    ...operationPasswordProperty
  }, ['command']),
  tool('leap_control_send', 'Send a real remote-control command. Requires LEAP_ALLOW_WRITE=1, allowWrite=true, and confirm=true.', {
    command: stringSchema('Command preset name'),
    windowPosition: integerSchema('S01 front-window position from 0 (maximum open) to 10 (closed); 8 is vent. Used with command=window.'),
    value: stringSchema('Legacy custom value for value-based commands; windowPosition takes priority'),
    cmdid: stringSchema('Custom command id'),
    stateJson: stringSchema('Custom state JSON'),
    ...operationPasswordProperty,
    allowWrite: booleanSchema('Must be true'),
    confirm: booleanSchema('Must be true')
  }, ['command', 'allowWrite', 'confirm']),
  tool('leap_control_query', 'Query remote-control result by msgID/eventId.', {
    msgID: stringSchema('Control msgID/eventId')
  }, ['msgID'])
];

const toolMap = new Map(tools.map((item) => [item.name, item]));

await client.loadSession();

const rl = createInterface({ input: process.stdin, crlfDelay: Infinity });
rl.on('line', async (line) => {
  if (!line.trim()) return;
  let request;
  try {
    request = JSON.parse(line);
    const response = await handleRequest(request);
    if (response) write(response);
  } catch (error) {
    write({
      jsonrpc: '2.0',
      id: request?.id ?? null,
      error: { code: -32000, message: error.message }
    });
  }
});

async function handleRequest(request) {
  const { id, method, params = {} } = request;
  if (method === 'initialize') {
    return {
      jsonrpc: '2.0',
      id,
      result: {
        protocolVersion: params.protocolVersion || '2024-11-05',
        capabilities: { tools: {} },
        serverInfo: { name: packageInfo.name, version: packageInfo.version }
      }
    };
  }
  if (method === 'notifications/initialized') return null;
  if (method === 'tools/list') {
    return { jsonrpc: '2.0', id, result: { tools } };
  }
  if (method === 'tools/call') {
    const result = await callTool(params.name, params.arguments || {});
    return {
      jsonrpc: '2.0',
      id,
      result: {
        content: [{ type: 'text', text: JSON.stringify(result, null, 2) }]
      }
    };
  }
  return {
    jsonrpc: '2.0',
    id,
    error: { code: -32601, message: `Method not found: ${method}` }
  };
}

async function callTool(name, args) {
  if (!toolMap.has(name)) throw new Error(`Unknown tool: ${name}`);
  switch (name) {
    case 'leap_session':
      return { session: client.sessionSummary(), auth: client.authDebugSummary() };
    case 'leap_refresh_token':
      return await client.refreshToken();
    case 'leap_refresh_old_token':
      return await client.refreshOldToken({ phone: args.phone || process.env.LEAP_PHONE || '' });
    case 'leap_send_sms':
      return { response: await client.sendSms(required(args.phone, 'phone')) };
    case 'leap_login_sms':
      return await client.loginWithSms({
        phone: required(args.phone, 'phone'),
        smsCode: required(args.smsCode, 'smsCode')
      });
    case 'leap_vehicles':
      return await client.listVehicles();
    case 'leap_select_vehicle':
      return { session: await client.selectVehicle(required(args.vin, 'vin'), args.carType || '') };
    case 'leap_route':
      return await client.getVehicleRoute();
    case 'leap_state':
      return await client.getVehicleState();
    case 'leap_mileage':
      return await client.getMileageEnergy();
    case 'leap_location':
      return await client.getLocation();
    case 'leap_overview':
      return await client.getOverview();
    case 'leap_commands':
      return { commands: commandPresets };
    case 'leap_control_preview':
      return await client.previewControl(controlArgs(args));
    case 'leap_control_send':
      if (process.env.LEAP_ALLOW_WRITE !== '1') {
        throw new Error('真实控车需要 MCP 服务启动时设置 LEAP_ALLOW_WRITE=1');
      }
      if (args.allowWrite !== true || args.confirm !== true) {
        throw new Error('真实控车需要 allowWrite=true 且 confirm=true');
      }
      return await client.sendControl({ ...controlArgs(args), allowWrite: true, confirm: true });
    case 'leap_control_query':
      return await client.queryControlResult(required(args.msgID, 'msgID'));
    default:
      throw new Error(`Unhandled tool: ${name}`);
  }
}

function controlArgs(args) {
  return {
    command: required(args.command, 'command'),
    value: args.windowPosition ?? args.value,
    cmdid: args.cmdid,
    stateJson: args.stateJson,
    operationPassword: args.operationPassword || defaultOperationPassword
  };
}

function tool(name, description, properties, required = []) {
  return {
    name,
    description,
    inputSchema: {
      type: 'object',
      properties,
      required,
      additionalProperties: false
    }
  };
}

function stringSchema(description) {
  return { type: 'string', description };
}

function booleanSchema(description) {
  return { type: 'boolean', description };
}

function integerSchema(description) {
  return { type: 'integer', minimum: 0, maximum: 10, description };
}

function required(value, name) {
  if (value === undefined || value === null || value === '') throw new Error(`Missing required argument: ${name}`);
  return value;
}

function write(message) {
  process.stdout.write(`${JSON.stringify(message)}\n`);
}
