import { mkdir, readFile, unlink, writeFile } from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';

export const DEFAULT_APP_VERSION = '1.22.87';
export const DEFAULT_SUB_VERSION = '3.19.2-2';
export const DEFAULT_SESSION_FILE = path.resolve(process.cwd(), '.leap-session.json');

const DEFAULT_PUBLIC_KEY =
  'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDHUIQKhkwNqJFTZPe98mC1lmpbY9r/+7PEWZg8ebqYXT3sumKRaQ0zcoTx42x0iybmCRXy4CcZrgGAbwKzwqwNw0rFquJ6c7mgQA6k3lZU3p96qBlzK7DSkoFR6mO9pjcd2hlJ8wH+IwI5b8IWWZhwVN/4cM7npG0S0zeRn3soEwIDAQAB';

const HOSTS = {
  appUser: 'https://appuser.leapmotor.cn',
  global: 'https://app-gw-global-master.leapmotor.com'
};
const ACCESS_TOKEN_REFRESH_LEEWAY_MS = 5 * 60 * 1000;
const OLD_TOKEN_REFRESH_LEEWAY_MS = 60 * 1000;

export const appointmentCmdIds = new Set(['161', '171', '361', '392']);

export const commandPresets = {
  lock: { label: '上锁', cmdid: '110', state: { value: 'lock' }, acceptsOperationPassword: true },
  unlock: { label: '解锁', cmdid: '110', state: { value: 'unlock' }, acceptsOperationPassword: true },
  trunk: { label: '后备箱/尾门触发', cmdid: '130', state: { value: 'true' }, acceptsOperationPassword: true },
  trunkOpen: { label: '后备箱/尾门打开', cmdid: '130', state: { value: 'true' }, acceptsOperationPassword: true },
  trunkClose: { label: '后备箱/尾门关闭', cmdid: '130', state: { value: 'false' }, acceptsOperationPassword: true },
  frunkOpen: { label: '前备箱打开', cmdid: '131', state: { value: '100' }, acceptsOperationPassword: true },
  frunkClose: { label: '前备箱关闭', cmdid: '131', state: { value: '0' }, acceptsOperationPassword: true },
  horn: { label: '鸣笛寻车', cmdid: '120', state: { value: 'true' }, acceptsOperationPassword: true },
  batteryPreheat: { label: '电池/座舱预热开启', cmdid: '160', state: { value: 'ptcon' }, acceptsOperationPassword: true },
  batteryPreheatOff: { label: '电池/座舱预热关闭', cmdid: '160', state: { value: 'ptcoff' }, acceptsOperationPassword: true },
  // Window values are car-type dependent (App helper o0000O0O.OooO00o.OooO0O0).
  // S01 close was verified from a live App request as value 10 with oppwd.
  window: { label: '车窗控制', cmdid: '230', windowMode: 'value', acceptsOperationPassword: true },
  windowClose: { label: '车窗关闭', cmdid: '230', windowMode: 'close', acceptsOperationPassword: true },
  windowVent: { label: '车窗通风', cmdid: '230', windowMode: 'vent', acceptsOperationPassword: true },
  windowOpen: { label: '车窗打开', cmdid: '230', windowMode: 'open', acceptsOperationPassword: true },
  sunshadeOpen: { label: '天幕/遮阳帘打开', cmdid: '240', state: { value: '10' }, acceptsOperationPassword: true },
  sunshadeClose: { label: '天幕/遮阳帘关闭', cmdid: '240', state: { value: '0' }, acceptsOperationPassword: true },
  sunroofOpen: { label: '天窗/车顶打开', cmdid: '300', state: { value: '1' }, acceptsOperationPassword: true },
  sunroofClose: { label: '天窗/车顶关闭', cmdid: '300', state: { value: '0' }, acceptsOperationPassword: true },
  ac: { label: '空调自定义', cmdid: '170', rawState: true, acceptsOperationPassword: true },
  acOn: {
    label: '空调开启',
    cmdid: '170',
    state: {
      operate: 'manual',
      temperature: '24',
      windlevel: '3',
      mode: 'cold',
      circle: 'in',
      wshld: '0',
      position: 'all'
    },
    acceptsOperationPassword: true
  },
  acOff: {
    label: '空调关闭',
    cmdid: '170',
    state: {
      operate: 'off',
      temperature: '24',
      windlevel: '3',
      mode: 'nohotcold',
      circle: 'out',
      wshld: '0',
      position: 'all'
    },
    acceptsOperationPassword: true
  },
  quickCool: {
    label: '极速降温',
    cmdid: '170',
    state: {
      operate: 'manual',
      temperature: '18',
      windlevel: '7',
      mode: 'cold',
      circle: 'in',
      wshld: '0',
      position: 'all'
    },
    acceptsOperationPassword: true
  },
  quickHeat: {
    label: '极速升温',
    cmdid: '170',
    state: {
      operate: 'manual',
      temperature: '32',
      windlevel: '7',
      mode: 'hot',
      circle: 'in',
      wshld: '0',
      position: 'all'
    },
    acceptsOperationPassword: true
  },
  windshieldDefrost: {
    label: '前挡除雾',
    cmdid: '170',
    state: {
      operate: 'manual',
      temperature: '32',
      windlevel: '7',
      mode: 'hot',
      circle: 'in',
      wshld: '1',
      position: 'all'
    },
    acceptsOperationPassword: true
  },
  custom: { label: '自定义命令', custom: true, acceptsOperationPassword: true }
};

export class LeapmotorChinaClient {
  constructor(options = {}) {
    this.sessionFile = options.sessionFile || DEFAULT_SESSION_FILE;
    this.hosts = { ...HOSTS, ...(options.hosts || {}) };
    this.allowWrite = Boolean(options.allowWrite);
    this.state = {
      deviceId: options.deviceId || crypto.randomUUID().replace(/-/g, ''),
      appVersion: options.appVersion || DEFAULT_APP_VERSION,
      subVersion: options.subVersion || DEFAULT_SUB_VERSION,
      phone: options.phone || '',
      oldAuth: null,
      newAuth: null,
      vehicles: [],
      selectedVin: '',
      selectedCarType: '',
      route: null
    };
    this.refreshPromise = null;
    this.oldRefreshPromise = null;
  }

  async loadSession() {
    try {
      const saved = JSON.parse(await readFile(this.sessionFile, 'utf8'));
      if (saved.deviceId) this.state.deviceId = saved.deviceId;
      if (saved.appVersion) this.state.appVersion = saved.appVersion;
      if (saved.subVersion) this.state.subVersion = saved.subVersion;
      if (saved.phone) this.state.phone = String(saved.phone);
      this.state.oldAuth = saved.oldAuth || null;
      if (this.state.oldAuth && !this.state.oldAuth.tokenExpiresAt && this.state.oldAuth.tokenExpired && saved.savedAt) {
        const seconds = Number(this.state.oldAuth.tokenExpired);
        const obtainedAt = Date.parse(saved.savedAt);
        if (seconds > 0 && Number.isFinite(obtainedAt)) {
          this.state.oldAuth = {
            ...this.state.oldAuth,
            tokenObtainedAt: obtainedAt,
            tokenExpiresAt: obtainedAt + seconds * 1000
          };
        }
      }
      this.state.newAuth = saved.newAuth
        ? { ...saved.newAuth, signKey: Buffer.from(saved.newAuth.signKey || '', 'base64') }
        : null;
      this.state.vehicles = Array.isArray(saved.vehicles) ? saved.vehicles : [];
      this.state.selectedVin = saved.selectedVin || '';
      this.state.selectedCarType = saved.selectedCarType || '';
      this.state.route = saved.route || null;
      return { loaded: true, session: this.sessionSummary() };
    } catch (error) {
      if (error.code === 'ENOENT') return { loaded: false, session: this.sessionSummary() };
      throw error;
    }
  }

  async saveSession() {
    await mkdir(path.dirname(this.sessionFile), { recursive: true });
    await writeFile(this.sessionFile, JSON.stringify(this.serializeSession(), null, 2), { mode: 0o600 });
    return this.sessionSummary();
  }

  async resetSession() {
    this.state.oldAuth = null;
    this.state.newAuth = null;
    this.state.phone = '';
    this.state.vehicles = [];
    this.state.selectedVin = '';
    this.state.selectedCarType = '';
    this.state.route = null;
    try {
      await unlink(this.sessionFile);
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
    }
    return this.sessionSummary();
  }

  serializeSession() {
    return {
      deviceId: this.state.deviceId,
      appVersion: this.state.appVersion,
      subVersion: this.state.subVersion,
      phone: this.state.phone || '',
      oldAuth: this.state.oldAuth,
      newAuth: this.state.newAuth
        ? { ...this.state.newAuth, signKey: this.state.newAuth.signKey.toString('base64') }
        : null,
      vehicles: this.state.vehicles,
      selectedVin: this.state.selectedVin,
      selectedCarType: this.state.selectedCarType,
      route: this.state.route,
      savedAt: new Date().toISOString()
    };
  }

  sessionSummary() {
    const accessTokenExpiresAt = getAccessTokenExpiresAt(this.state.newAuth);
    const accessTokenValid = Boolean(accessTokenExpiresAt && accessTokenExpiresAt > Date.now());
    const oldTokenExpiresAt = getOldTokenExpiresAt(this.state.oldAuth);
    const oldTokenRemainingMs = oldTokenExpiresAt ? oldTokenExpiresAt - Date.now() : null;
    return {
      deviceId: this.state.deviceId,
      oldLogin: Boolean(this.state.oldAuth?.token),
      newLogin: accessTokenValid,
      accessTokenValid,
      accessTokenExpiresAt: accessTokenExpiresAt ? new Date(accessTokenExpiresAt).toISOString() : '',
      oldTokenValid: Boolean(this.state.oldAuth?.token && (!oldTokenExpiresAt || oldTokenExpiresAt > Date.now())),
      oldTokenExpiresAt: oldTokenExpiresAt ? new Date(oldTokenExpiresAt).toISOString() : '',
      oldTokenRemainingSec: oldTokenRemainingMs == null ? null : Math.max(0, Math.floor(oldTokenRemainingMs / 1000)),
      phonePresent: Boolean(this.state.phone),
      accountIdMasked: maskValue(this.state.newAuth?.accountId || this.state.oldAuth?.accountId || ''),
      selectedVin: this.state.selectedVin,
      selectedVinMasked: maskValue(this.state.selectedVin),
      selectedCarType: this.state.selectedCarType,
      vehicleCount: this.state.vehicles.length,
      appRegion: this.state.route?.appRegion || '',
      appCenter: this.state.route?.appCenter || '',
      sessionFile: this.sessionFile
    };
  }

  authDebugSummary() {
    const auth = this.state.newAuth;
    return {
      oldTokenPresent: Boolean(this.state.oldAuth?.token),
      newTokenPresent: Boolean(auth?.accessToken),
      oldAccountIdMasked: maskValue(this.state.oldAuth?.accountId || ''),
      newAccountIdMasked: maskValue(auth?.accountId || ''),
      gatewayAccountIdMasked: maskValue(auth?.gatewayAccountId || ''),
      signKeyBytes: auth?.signKey?.length || 0,
      signKeySha256Prefix: auth?.signKey ? sha256(auth.signKey).slice(0, 12) : '',
      tokenPayload: jwtPayloadSummary(auth?.accessToken || '', this.state)
    };
  }

  async sendSms(phone) {
    if (!phone) throw new Error('phone 不能为空');
    const url = `${this.hosts.appUser}/app-user/applogin/compliance/sendmessagecode`;
    return fetchJson(url, {
      method: 'GET',
      headers: this.oldAppHeaders(),
      query: { phoneNo: phoneNoCiphertext(phone) }
    });
  }

  async loginWithSms(options = {}) {
    if (!options.phone || !options.smsCode) throw new Error('phone 和 smsCode 不能为空');
    const url = `${this.hosts.appUser}/app-user/applogin/check_login_with_phone`;
    const params = {
      phoneNoCiphertext: phoneNoCiphertext(options.phone),
      smsCode: options.smsCode,
      deviceID: this.state.deviceId,
      smDeviceId: options.smDeviceId || this.state.deviceId,
      os: 'android',
      pageUrl: options.pageUrl || ''
    };
    for (const key of ['oaid', 'referrer', 'captchaOutput', 'genTime', 'lotNumber', 'passToken', 'requestId']) {
      if (options[key]) params[key] = options[key];
    }
    if (options.captchaPhone) params.phone = options.captchaPhone;

    const loginResponse = await fetchJson(url, {
      method: 'POST',
      headers: this.oldAppHeaders(),
      query: params,
      queryPost: true
    });
    this.state.phone = String(options.phone).trim();
    this.state.oldAuth = extractOldAuth(loginResponse);
    const exchangeResponse = await this.exchangeNewGateway();
    await this.saveSession();
    return {
      login: sanitizeLoginResponse(loginResponse),
      exchanged: Boolean(this.state.newAuth?.accessToken),
      exchangeCode: exchangeResponse?.result ?? exchangeResponse?.code ?? null,
      signKeyBytes: this.state.newAuth?.signKey?.length || 0,
      session: this.sessionSummary()
    };
  }

  async exchangeNewGateway() {
    this.requireOldAuth();
    const url = `${this.hosts.global}/base/base-user/account/v1/login`;
    const body = {
      identifier: this.state.oldAuth.accountId,
      identifierType: '1',
      security: this.state.oldAuth.token
    };
    const response = await fetchJson(url, {
      method: 'POST',
      headers: this.newGatewayHeaders(body, false),
      jsonBody: body
    });
    this.state.newAuth = extractNewAuth(response, this.state.oldAuth);
    return response;
  }

  async refreshToken() {
    this.requireNewAuth();
    if (!this.state.newAuth.refreshToken) throw new Error('缺少 refreshToken，无法自动续期');
    if (this.refreshPromise) return this.refreshPromise;
    this.refreshPromise = this.performTokenRefresh();
    try {
      return await this.refreshPromise;
    } finally {
      this.refreshPromise = null;
    }
  }

  async performTokenRefresh() {
    const url = `${this.hosts.global}/base/base-user/token/v1/refresh`;
    const body = { refreshToken: this.state.newAuth.refreshToken };
    const response = await fetchJson(url, {
      method: 'POST',
      headers: this.newGatewayHeaders(body, true),
      jsonBody: body
    });
    this.state.newAuth = extractNewAuth(response, this.state.oldAuth);
    await this.saveSession();
    return { refreshed: true, kind: 'gateway', session: this.sessionSummary() };
  }

  /**
   * Refresh the old App token used by remote-control / carownerservice signatures.
   * App: GET /app-user/appuseroperate/getnewtoken with MD5Util.getRequestMapWithRtoken.
   */
  async refreshOldToken(options = {}) {
    this.requireOldAuth();
    if (!this.state.oldAuth.refreshToken) throw new Error('缺少旧 refreshToken，无法续期远控 token');
    const phone = String(options.phone || this.state.phone || '').trim();
    if (!phone) {
      throw new Error('旧 token 续期需要手机号：请重新短信登录（会写入 session.phone），或传入 phone');
    }
    this.state.phone = phone;
    if (this.oldRefreshPromise) return this.oldRefreshPromise;
    this.oldRefreshPromise = this.performOldTokenRefresh(phone);
    try {
      return await this.oldRefreshPromise;
    } finally {
      this.oldRefreshPromise = null;
    }
  }

  async performOldTokenRefresh(phone) {
    // Frida-captured App request (TokenRefreshManager / MD5Util.getRequestMapWithRtoken):
    // GET /app-user/appuseroperate/getnewtoken
    // query: accountId, accountNumber=<RSA phone>, deviceID, nonce, timespan, signStr
    // header: XFX-CDN-CROSS-REFRESH-NODE=<old refreshToken>
    // sign: md5(sorted values incl. refreshtoken).slice(8,24); refreshtoken not sent in query
    const accountNumber = phoneNoCiphertext(phone);
    const accountId = String(this.state.oldAuth.accountId || '');
    const signed = this.oldSignedParamsWithRefreshToken({
      accountId,
      accountNumber
    });
    const url = `${this.hosts.appUser}/app-user/appuseroperate/getnewtoken`;
    const headers = {
      ...this.oldAppHeaders(),
      'XFX-CDN-CROSS-REFRESH-NODE': this.state.oldAuth.refreshToken
    };
    const response = await fetchJson(url, {
      method: 'GET',
      headers,
      query: signed
    });
    const data = response?.data || response;
    const token = data?.token || '';
    const code = response?.code ?? response?.result;
    if (!token || !(code === 200 || code === 0 || code === '200' || code === '0' || response?.success === true)) {
      const err = new Error(
        `旧 token 续期失败：${response?.msg || response?.message || `code=${code}`}`
      );
      err.response = response;
      throw err;
    }
    const tokenExpired = data?.tokenExpired != null ? String(data.tokenExpired) : '21600';
    const obtainedAt = Date.now();
    this.state.oldAuth = {
      ...this.state.oldAuth,
      token: String(token),
      refreshToken: data?.refreshToken ? String(data.refreshToken) : this.state.oldAuth.refreshToken,
      tokenExpired,
      tokenObtainedAt: obtainedAt,
      tokenExpiresAt: obtainedAt + (Number(tokenExpired) > 0 ? Number(tokenExpired) * 1000 : 21600 * 1000)
    };
    await this.saveSession();
    // Re-exchange gateway credentials bound to the old security token.
    try {
      await this.exchangeNewGateway();
      await this.saveSession();
    } catch {
      // best-effort
    }
    return {
      refreshed: true,
      kind: 'old',
      tokenExpired,
      session: this.sessionSummary()
    };
  }

  oldSignedParamsWithRefreshToken(params = {}) {
    this.requireOldAuth();
    const map = {
      timespan: String(Date.now()),
      nonce: randomNonce(),
      deviceID: this.state.deviceId,
      refreshtoken: this.state.oldAuth.refreshToken,
      ...Object.fromEntries(Object.entries(params).map(([key, value]) => [key, asString(value)]))
    };
    const signStr = shortMd5(sortAndConcatValues(map));
    delete map.refreshtoken;
    map.signStr = signStr;
    return map;
  }

  async ensureFreshAccessToken() {
    this.requireNewAuth();
    const expiresAt = getAccessTokenExpiresAt(this.state.newAuth);
    if (expiresAt && expiresAt - Date.now() <= ACCESS_TOKEN_REFRESH_LEEWAY_MS) {
      await this.refreshToken();
      return true;
    }
    return false;
  }

  async ensureFreshOldToken() {
    this.requireOldAuth();
    const expiresAt = getOldTokenExpiresAt(this.state.oldAuth);
    if (!expiresAt) return false;
    if (expiresAt - Date.now() > OLD_TOKEN_REFRESH_LEEWAY_MS) return false;
    await this.refreshOldToken();
    return true;
  }

  async authenticatedFetchJson(url, optionsFactory) {
    await this.ensureFreshAccessToken();
    const request = () => fetchJson(url, optionsFactory());
    try {
      const response = await request();
      if (!isAuthFailureResponse(response)) return response;
    } catch (error) {
      if (!isAuthFailureError(error)) throw error;
    }
    await this.refreshToken();
    return request();
  }

  async oldAuthenticatedFetchJson(url, optionsFactory) {
    try {
      await this.ensureFreshOldToken();
    } catch {
      // Best-effort: still attempt the request; caller may get result=39.
    }
    const request = () => fetchJson(url, optionsFactory());
    const response = await request();
    if (!isOldAuthFailureResponse(response)) return response;
    await this.refreshOldToken();
    return request();
  }

  async listVehicles() {
    this.requireNewAuth();
    const url = `${this.hosts.global}/app/app-global-service/v1/vehicle/list`;
    const response = await this.authenticatedFetchJson(url, () => ({
      method: 'GET',
      headers: this.newGatewayHeaders({}, true)
    }));
    this.state.vehicles = vehicleListFromResponse(response);
    if (!this.state.selectedVin && this.state.vehicles[0]?.vin) {
      this.state.selectedVin = this.state.vehicles[0].vin;
      this.state.selectedCarType = this.state.vehicles[0].carType || this.state.vehicles[0].cartype || '';
      this.state.route = null;
    }
    await this.saveSession();
    return { vehicles: this.state.vehicles, raw: response, session: this.sessionSummary() };
  }

  async selectVehicle(vin, carType = '') {
    if (!vin) throw new Error('vin 不能为空');
    this.state.selectedVin = String(vin);
    const vehicle = this.state.vehicles.find((item) => item.vin === this.state.selectedVin);
    this.state.selectedCarType = String(carType || vehicle?.carType || vehicle?.cartype || '');
    this.state.route = null;
    await this.saveSession();
    return this.sessionSummary();
  }

  async getVehicleRoute() {
    this.requireVin();
    const url = `${this.hosts.global}/app/app-global-service/v1/vehicle/getCarRoute`;
    const params = { vin: this.state.selectedVin };
    const response = await this.authenticatedFetchJson(url, () => ({
      method: 'GET',
      headers: this.newGatewayHeaders(params, true),
      query: params
    }));
    this.state.route = routeFromResponse(response);
    await this.saveSession();
    return { route: this.state.route, raw: response, session: this.sessionSummary() };
  }

  async ensureRoute() {
    this.requireVin();
    if (this.state.route?.appRegion) return this.state.route;
    await this.getVehicleRoute();
    if (!this.state.route?.appRegion) throw new Error('车辆路由缺少 appRegion，无法继续');
    return this.state.route;
  }

  async getVehicleState() {
    const route = await this.ensureRoute();
    const url = `${pickHost(route.appRegion)}/app/app-signal-service/signal/info/query`;
    const body = { vin: this.state.selectedVin };
    const raw = await this.authenticatedFetchJson(url, () => ({
      method: 'POST',
      headers: this.newGatewayHeaders(body, true),
      jsonBody: body
    }));
    const signalMap = extractSignalMap(raw);
    return {
      raw,
      summary: vehicleStateSummary(raw),
      fieldDefinitions: describeVehicleStateFields(signalMap),
      session: this.sessionSummary()
    };
  }

  async getMileageEnergy() {
    this.requireVin();
    try {
      await this.ensureFreshOldToken();
    } catch {
      // continue; may still succeed if token not expired yet
    }
    const raw = await this.oldSignedFetchFirst(
      '/carownerservice/v3/api/drivingrecord/mileage/energy/detail',
      { vin: this.state.selectedVin }
    );
    return { raw, session: this.sessionSummary() };
  }

  async getLocation() {
    this.requireVin();
    try {
      await this.ensureFreshOldToken();
    } catch {
      // continue
    }
    const raw = await this.oldSignedFetchFirst(
      '/carownerservice/v3/api/chassis/query',
      { vin: this.state.selectedVin }
    );
    return { raw, session: this.sessionSummary() };
  }

  async getOverview() {
    await this.ensureRoute();
    const [route, carState, mileage, location] = await Promise.allSettled([
      this.getVehicleRoute(),
      this.getVehicleState(),
      this.getMileageEnergy(),
      this.getLocation()
    ]);
    return { route, carState, mileage, location, session: this.sessionSummary() };
  }

  buildCommand(input = {}) {
    const preset = commandPresets[input.command || ''];
    if (!preset) throw new Error(`未知控车命令: ${input.command}`);
    let cmdid = preset.cmdid;
    let stateJson;

    if (preset.custom) {
      cmdid = String(input.cmdid || '').trim();
      if (!cmdid) throw new Error('自定义命令需要 cmdid');
      stateJson = normalizeStateJson(input.stateJson);
    } else if (preset.rawState) {
      stateJson = normalizeStateJson(input.stateJson);
    } else if (preset.windowMode) {
      const value = resolveWindowControlValue({
        carType: this.state.selectedCarType,
        mode: preset.windowMode,
        explicitValue: input.value
      });
      stateJson = JSON.stringify({ value });
    } else if (preset.stateFromValue) {
      stateJson = JSON.stringify({ value: asString(input.value || '0') });
    } else {
      stateJson = JSON.stringify(preset.state);
    }
    return {
      cmdid: String(cmdid),
      state: stateJson,
      label: preset.label,
      acceptsOperationPassword: preset.acceptsOperationPassword === true,
      requiresOperationPassword: preset.acceptsOperationPassword === true
    };
  }

  async previewControl(input = {}) {
    const route = await this.ensureRoute();
    const command = this.buildCommand(input);
    const params = {
      cmdid: command.cmdid,
      state: command.state,
      carvin: this.state.selectedVin
    };
    if (input.operationPassword) params.oppwd = encryptOperationPassword(input.operationPassword, this.state.oldAuth);
    const signed = this.oldSignedParams(params);
    const appointment = appointmentCmdIds.has(command.cmdid);
    const host = appointment ? pickHost(route.appCenter || route.appRegion) : pickHost(route.appRegion);
    const url = appointment
      ? `${host}/carownerservice/v3/api/appremotectl/appointment`
      : `${host}/app/app-control-service/v3/api/appremotectl`;
    return {
      dryRun: true,
      appointment,
      method: 'POST',
      url,
      command,
      body: redactControlBody(signed),
      session: this.sessionSummary()
    };
  }

  async sendControl(input = {}) {
    if (!this.allowWrite && input.allowWrite !== true) {
      throw new Error('真实控车默认禁用：需要 SDK allowWrite=true 或参数 allowWrite=true');
    }
    if (input.confirm !== true) throw new Error('真实控车需要 confirm=true');
    const command = this.buildCommand(input);
    if (command.requiresOperationPassword && !input.operationPassword) {
      throw new Error('真实控车需要操作密码：请传 operationPassword/--pin，或为 MCP 配置 LEAP_OPERATION_PASSWORD');
    }
    try {
      await this.ensureFreshOldToken();
    } catch {
      // continue; request may still work until hard expiry
    }
    const preview = await this.previewControl(input);
    const buildBody = () => this.oldSignedParams({
      cmdid: preview.command.cmdid,
      state: preview.command.state,
      carvin: this.state.selectedVin,
      ...(input.operationPassword
        ? { oppwd: encryptOperationPassword(input.operationPassword, this.state.oldAuth) }
        : {})
    });
    let raw = await fetchJson(preview.url, {
      method: 'POST',
      headers: this.oldAppHeaders(),
      formBody: buildBody()
    });
    if (isOldAuthFailureResponse(raw)) {
      await this.refreshOldToken();
      raw = await fetchJson(preview.url, {
        method: 'POST',
        headers: this.oldAppHeaders(),
        formBody: buildBody()
      });
    }
    return { dryRun: false, command: preview.command, raw, session: this.sessionSummary() };
  }

  async queryControlResult(msgID) {
    if (!msgID) throw new Error('msgID 不能为空');
    const route = await this.ensureRoute();
    const url = `${pickHost(route.appRegion)}/app/app-control-service/v3/api/appremotectl/query`;
    try {
      await this.ensureFreshOldToken();
    } catch {
      // continue
    }
    const raw = await this.oldAuthenticatedFetchJson(url, () => ({
      method: 'GET',
      headers: this.oldAppHeaders(),
      query: this.oldSignedParams({ msgID })
    }));
    return { raw, session: this.sessionSummary() };
  }

  async oldServiceCandidates(pathname, params) {
    const route = await this.ensureRoute();
    const hosts = [this.hosts.global, pickHost(route.appRegion), pickHost(route.appCenter)].filter(Boolean);
    return [...new Set(hosts)].map((host) => ({
      label: host,
      url: `${host}${pathname}`,
      options: {
        method: 'GET',
        headers: this.oldAppHeaders(),
        query: this.oldSignedParams(params)
      }
    }));
  }

  async oldSignedFetchFirst(pathname, params) {
    const candidates = await this.oldServiceCandidates(pathname, params);
    try {
      const raw = await fetchFirstJson(candidates);
      if (!isOldAuthFailureResponse(raw)) return raw;
    } catch (error) {
      if (!isOldAuthFailureError(error)) throw error;
    }
    await this.refreshOldToken();
    return fetchFirstJson(await this.oldServiceCandidates(pathname, params));
  }

  oldAppHeaders() {
    const headers = {
      APPPlatform: 'Android',
      APPVersion: this.state.appVersion,
      APPImei: this.state.deviceId,
      'C-VERSIONS': 'APP',
      'XFX-CDN-VRS': 'v4'
    };
    if (this.state.oldAuth?.token) headers['XFX-CDN-CROSS-NODE'] = this.state.oldAuth.token;
    return headers;
  }

  newGatewayHeaders(params = {}, needLogin = true) {
    const nonce = randomNonce();
    const timestamp = String(Date.now());
    const headers = {
      source: 'leapmotor',
      channel: '1',
      acceptLanguage: 'zh-CN',
      'x-region': 'CN',
      'x-api-signature-version': '2.0',
      digest: '',
      version: this.state.appVersion,
      deviceType: 'android',
      nonce,
      timestamp,
      deviceId: this.state.deviceId,
      userId: this.state.newAuth?.accountId || this.state.oldAuth?.accountId || '',
      carvin: this.state.selectedVin || '',
      cartype: this.state.selectedCarType || '',
      'x-subversion': this.state.subVersion
    };
    const signHeaders = {
      acceptLanguage: headers.acceptLanguage,
      channel: headers.channel,
      deviceId: headers.deviceId,
      deviceType: headers.deviceType,
      nonce: headers.nonce,
      source: headers.source,
      timestamp: headers.timestamp,
      version: headers.version
    };
    const signBase = sortAndConcatValues(signHeaders, params);
    if (needLogin) {
      this.requireNewAuth();
      headers.token = this.state.newAuth.accessToken;
      headers.userId = this.state.newAuth.accountId || headers.userId;
      headers.sign = crypto.createHmac('sha256', this.state.newAuth.signKey).update(signBase, 'utf8').digest('hex');
    } else {
      headers.sign = sha256(signBase);
    }
    return headers;
  }

  oldSignedParams(params = {}) {
    this.requireOldAuth();
    const map = {
      timespan: String(Date.now()),
      nonce: randomNonce(),
      deviceID: this.state.deviceId,
      token: this.state.oldAuth.token,
      ...Object.fromEntries(Object.entries(params).map(([key, value]) => [key, asString(value)]))
    };
    const signStr = shortMd5(sortAndConcatValues(map));
    delete map.token;
    map.signStr = signStr;
    return map;
  }

  requireOldAuth() {
    if (!this.state.oldAuth?.token || !this.state.oldAuth?.accountId) {
      throw new Error('还没有完成验证码登录，缺少旧 token/accountId');
    }
  }

  requireNewAuth() {
    if (!this.state.newAuth?.accessToken || !this.state.newAuth?.signKey || this.state.newAuth.signKey.length === 0) {
      throw new Error('还没有完成新网关 token 交换，缺少 accessToken/signKey');
    }
  }

  requireVin() {
    if (!this.state.selectedVin) throw new Error('还没有选择车辆 VIN');
  }
}

export function createClientFromEnv(options = {}) {
  return new LeapmotorChinaClient({
    sessionFile: process.env.LEAP_SESSION_FILE || options.sessionFile || path.resolve(process.cwd(), '.leap-session.json'),
    deviceId: process.env.LEAP_DEVICE_ID || options.deviceId,
    appVersion: process.env.LEAP_APP_VERSION || options.appVersion,
    subVersion: process.env.LEAP_SUB_VERSION || options.subVersion,
    allowWrite: process.env.LEAP_ALLOW_WRITE === '1' || options.allowWrite
  });
}

export function vehicleStateSummary(raw) {
  const signalMap = extractSignalMap(raw);
  const pick = (...keys) => {
    for (const key of keys) {
      if (signalMap?.[key] !== undefined && signalMap?.[key] !== null) return signalMap[key];
    }
    return null;
  };
  return {
    battery: {
      soc: pick('soc'),
      expectedMileage: pick('expectedMileage'),
      batteryCurrent: pick('batteryCurrent'),
      batteryVoltage: pick('batteryVoltage'),
      chargeState: pick('chargeState'),
      chargeRemainTime: pick('chargeRemainTime'),
      dcInputFastCharge: pick('dcInputFastCharge'),
      chargesocSetting: pick('chargesocSetting')
    },
    locks: {
      driverDoorLockStatus: pick('driverDoorLockStatus'),
      bcmDoorCtrlAllow: pick('bcmDoorCtrlAllow')
    },
    doors: {
      driverDoor: pick('lbcmDriverDoorStatus'),
      passengerDoor: pick('rbcmDriverDoorStatus'),
      leftRearDoor: pick('lbcmLeftRearDoorStatus'),
      rightRearDoor: pick('rbcmRightRearDoorStatus'),
      trunk: pick('bbcmBackDoorStatus')
    },
    windows: {
      isSupportWindowsRemoteControl: pick('isSupportWindowsRemoteControl'),
      leftFrontWindowPercent: pick('leftFrontWindowPercent'),
      leftRearWindowPercent: pick('leftRearWindowPercent'),
      rightFrontWindowPercent: pick('rightFrontWindowPercent'),
      rightRearWindowPercent: pick('rightRearWindowPercent')
    },
    climate: {
      acSwitch: pick('acSwitch'),
      acSetting: pick('acSetting'),
      acAirVolume: pick('acAirVolume'),
      acAirVolumeSetting: pick('acAirVolumeSetting'),
      acWindDirection: pick('acWindDirection'),
      acCircleMode: pick('acCircleMode'),
      acCoolingAndHeating: pick('acCoolingAndHeating'),
      acTempMode: pick('acTempMode'),
      indoorTemp: pick('indoorTemp'),
      ptcState: pick('ptcState'),
      ptcPowerSettingValue: pick('ptcPowerSettingValue'),
      minSingleTemp: pick('minSingleTemp')
    },
    location: {
      latitude: pick('latitude'),
      longitude: pick('longitude'),
      privacyGPS: pick('privacyGPS'),
      privacyData: pick('privacyData')
    }
  };
}

const vehicleStateKnownFieldDefinitions = {
  soc: fieldDefinition('动力电池剩余电量', '%', '0-100', 'high'),
  chargeState: fieldDefinition('充电状态', null, 'App 代码确认：0 未插枪，1 充电中，2 充电完成，3 充电故障，4 预约/定时充电等待，6 充电暂停；5 为非充电态但精确子状态未确认', 'high'),
  chargeRemainTime: fieldDefinition('预计充电剩余时间', 'min', 'App 代码按 60 分钟换算小时；未充电时 0 不应解释为“无需充电”', 'high'),
  chargesocSetting: fieldDefinition('用户设置的充电 SOC 上限', '%', '0-100；表示充电上限设置，不是当前 SOC', 'high'),
  batteryCurrent: fieldDefinition('动力电池包电流', 'A (likely)', '正负方向尚未确认，不可据符号判断充电或放电', 'medium'),
  dumpEnergy: fieldDefinition('动力电池估算剩余能量', 'Wh (likely)', 'S01 实测 SOC=20 时为 9540，量级符合 Wh；仍需车型校准', 'medium'),
  expectedMileage: fieldDefinition('车辆估算剩余续航', 'km', '由车辆端算法估算，计算依据和刷新策略未公开', 'high'),
  batteryVoltage: fieldDefinition('动力电池包总电压', 'V (likely)', 'S01 实测为 337，量级符合高压电池包总电压', 'medium'),
  gearStatus: fieldDefinition('原始档位数字枚举', null, 'App 仅确认 1 或 3 会被视为行驶档；P/R/N/D 的完整数字映射仍未确认', 'low'),
  parkOutEnable: fieldDefinition('泊车驶出允许标志', 'boolean-like', 'signalMap 路径中字符串 1 转为允许；旧协议路径存在 0 转 true 的反向逻辑，跨协议不可直接复用', 'medium'),
  dcInputFastCharge: fieldDefinition('直流快充输入状态', 'boolean-like', 'App 代码按字符串判断：1=直流快充输入，0=非直流快充/慢充；不是充电功率', 'high'),
  bluetoothState: fieldDefinition('车载蓝牙开关状态', 'boolean', 'App 直接赋值为 isBluetoothOpen；表示开关，不表示已有设备连接', 'high'),
  hotspotState: fieldDefinition('车载热点开关状态', 'boolean', 'App 直接赋值为 isHotSpotOpen；表示开关，不表示已有客户端连接', 'high'),
  bluetoothAddr: fieldDefinition('车载蓝牙地址', null, '通常为蓝牙 MAC/设备地址；不是连接状态', 'medium'),
  acSwitch: fieldDefinition('空调开关状态', 'boolean', 'true 开启，false 关闭', 'high'),
  acTempMode: fieldDefinition('空调温控模式', 'boolean', 'App 映射：true=auto，false=manual', 'high'),
  acSetting: fieldDefinition('空调设定温度', '°C', '-1 表示无有效值；低于 18 显示 18，高于 32 显示 32', 'high'),
  acAirVolume: fieldDefinition('当前空调风量档位', null, '原始数字档位', 'medium'),
  acAirVolumeSetting: fieldDefinition('空调风量设定档位', null, '目标/设定风量；具体范围车型相关', 'medium'),
  acCoolingAndHeating: fieldDefinition('空调制冷制热模式', null, 'App 映射：0=nohotcold，1=hot，2=cold，3=hotcold', 'high'),
  acCircleMode: fieldDefinition('空调循环模式', 'boolean', 'App 映射：true=in 内循环，false=out 外循环', 'high'),
  acWindDirection: fieldDefinition('空调风向原始值', null, '具体数字/枚举映射尚未确认', 'low'),
  indoorTemp: fieldDefinition('车内温度', '°C (likely)', '单位尚未在代码中显式标注', 'medium'),
  outdoorTemp: fieldDefinition('车外温度', '°C (likely)', '单位尚未在代码中显式标注', 'medium'),
  bbcmBackDoorStatus: fieldDefinition('后备箱/尾门开启状态', 'boolean', 'true 开启，false 关闭', 'high'),
  lbcmDriverDoorStatus: fieldDefinition('主驾车门开启状态', 'boolean', 'true 开启，false 关闭', 'high'),
  rbcmDriverDoorStatus: fieldDefinition('副驾车门开启状态', 'boolean', 'true 开启，false 关闭', 'high'),
  lbcmLeftRearDoorStatus: fieldDefinition('左后车门开启状态', 'boolean', 'true 开启，false 关闭', 'high'),
  rbcmRightRearDoorStatus: fieldDefinition('右后车门开启状态', 'boolean', 'true 开启，false 关闭', 'high'),
  bcmDoorCtrlAllow: fieldDefinition('车门远控允许标志', 'boolean-like', 'App 将该值转为 Boolean 后作为 isDoorEnable', 'high'),
  driverDoorLockStatus: fieldDefinition('主驾/车门锁锁止状态', 'boolean', 'S01 锁车/解锁对照样本确认：true=已锁，false=已解锁', 'high'),
  bcmKeyPositionOn1: fieldDefinition('BCM 电源/钥匙档位 1 标志', 'boolean', 'App 保存为 isOn1Open；精确对应 ACC/ON 的哪一档仍未确认', 'medium'),
  bcmKeyPositionOn3: fieldDefinition('BCM 电源/钥匙档位 3 标志', 'boolean', 'App 保存为 isOn3Open，并据此判断远控状态；精确档位名仍未确认', 'medium'),
  latitude: fieldDefinition('车辆纬度', 'decimal degrees', '坐标系取决于接口/车辆路由，未在此字段定义中转换', 'high'),
  longitude: fieldDefinition('车辆经度', 'decimal degrees', '坐标系取决于接口/车辆路由，未在此字段定义中转换', 'high'),
  speed: fieldDefinition('车辆速度', 'km/h (likely)', '16777215 在 App 中表示无有效值', 'medium'),
  totalMileage: fieldDefinition('车辆总里程', 'km (likely)', '车型显示单位可能受用户单位设置影响', 'medium'),
  minSingleTemp: fieldDefinition('动力电池最低单体温度', '°C (likely)', '单位尚未在代码中显式标注', 'medium'),
  ptcPowerSettingValue: fieldDefinition('PTC 功率/加热设定原始值', null, 'App 以非 0 判断电池正在加热；精确单位未确认', 'medium'),
  ptcState: fieldDefinition('PTC 加热原始状态', null, '枚举映射尚未确认', 'low'),
  isSupportWindowsRemoteControl: fieldDefinition('车窗远控能力值', null, 'App 代码确认：原始值 2 转为支持=true；其他值映射未使用', 'high'),
  driverWindowStatus: windowStatusDefinition('主驾/左前'),
  rightFrontWindowStatus: windowStatusDefinition('右前'),
  leftRearWindowStatus: windowStatusDefinition('左后'),
  rightRearWindowStatus: windowStatusDefinition('右后'),
  chargeTimeSetting: fieldDefinition('预约充电时间设置', 'HH:mm-like string', 'App 按小时/分钟字符串展示', 'high'),
  collectTime: fieldDefinition('车辆信号采集时间', 'local datetime', '车辆/信号侧采集时间', 'high'),
  createTime: fieldDefinition('服务端信号记录创建时间', 'local datetime', '通常略晚于 collectTime，不等同于车辆采集时间', 'high'),
  leftFrontTirePressureState: tirePressureStateDefinition(),
  rightFrontTirePressureState: tirePressureStateDefinition(),
  leftRearTirePressureState: tirePressureStateDefinition(),
  rightRearTirePressureState: tirePressureStateDefinition(),
  leftFrontTirePressure: tirePressureDefinition(),
  rightFrontTirePressure: tirePressureDefinition(),
  leftRearTirePressure: tirePressureDefinition(),
  rightRearTirePressure: tirePressureDefinition(),
  leftFrontWindowPercent: windowPositionDefinition(),
  rightFrontWindowPercent: windowPositionDefinition(),
  leftRearWindowPercent: windowPositionDefinition(),
  rightRearWindowPercent: windowPositionDefinition()
};

export function describeVehicleStateFields(signalMap = {}) {
  return Object.fromEntries(Object.keys(signalMap || {}).sort().map((name) => [
    name,
    vehicleStateKnownFieldDefinitions[name] || fieldDefinition(
      '车端原始 signalMap 字段，当前版本尚未确认语义',
      null,
      '按原值透传；智能体不得根据字段名自行补全枚举、单位或业务含义',
      'unknown'
    )
  ]));
}

function fieldDefinition(meaning, unit, valueMeaning, confidence) {
  return { meaning, unit, valueMeaning, confidence };
}

function tirePressureStateDefinition() {
  return fieldDefinition('轮胎压力告警状态', 'boolean-like', 'App 代码确认字符串 1=异常/告警，0=无告警', 'high');
}

function tirePressureDefinition() {
  return fieldDefinition('轮胎压力', 'kPa (S01 verified)', 'S01 实测 249-261，与 kPa 量级一致；其他车型应结合车型验证', 'high');
}

function windowPositionDefinition() {
  return fieldDefinition('车窗位置刻度', 'S01: 0-10', 'S01 前窗 0 最大打开、8 微开、10 关闭；后窗在当前车辆不参与远控', 'high');
}

function windowStatusDefinition(position) {
  return fieldDefinition(`${position}车窗开关状态`, 'boolean', 'S01 动态样本确认：true 打开，false 关闭；位置请优先看对应 WindowPercent', 'high');
}

export function extractSignalMap(raw) {
  return findObject(raw, (obj) => obj.signalMap && typeof obj.signalMap === 'object')?.signalMap
    || raw?.data?.signalMap
    || raw?.signalMap
    || {};
}

function randomNonce() {
  return String(Math.floor(Math.random() * 10_000_000) + 10_000);
}

function asString(value) {
  if (value === null || value === undefined) return '';
  if (typeof value === 'string') return value;
  return String(value);
}

/**
 * App window helper (cmdid 230) values by car type.
 * Source: apk-analysis notes / o0000O0O.OooO00o.OooO0O0(mode)
 *
 * | mode  | T03 | S01 | other |
 * | close | 0   | 10  | 0     |
 * | vent  | 20  | 8   | 2     |
 * | open  | 50  | 0   | 5     |
 */
export function resolveWindowControlValue({ carType = '', mode = 'close', explicitValue } = {}) {
  const type = String(carType || '').toUpperCase();
  const family = type.startsWith('T03') ? 'T03' : type.startsWith('S01') ? 'S01' : 'OTHER';
  if (explicitValue !== undefined && explicitValue !== null && String(explicitValue).trim() !== '') {
    if (family === 'S01') {
      const number = Number(explicitValue);
      if (!Number.isInteger(number) || number < 0 || number > 10) {
        throw new Error('S01 车窗 value 必须是 0 到 10 的整数');
      }
    }
    return asString(explicitValue);
  }
  const table = {
    T03: { close: '0', vent: '20', open: '50' },
    S01: { close: '10', vent: '8', open: '0' },
    OTHER: { close: '0', vent: '2', open: '5' }
  };
  const key = mode === 'value' ? 'close' : mode;
  const row = table[family] || table.OTHER;
  return row[key] || row.close;
}

function sortAndConcatValues(...maps) {
  const merged = {};
  for (const map of maps) {
    if (!map) continue;
    for (const [key, value] of Object.entries(map)) {
      if (value !== null && value !== undefined) merged[key] = asString(value);
    }
  }
  return Object.keys(merged).sort().map((key) => merged[key]).join('');
}

function shortMd5(input) {
  return crypto.createHash('md5').update(input, 'utf8').digest('hex').slice(8, 24);
}

function sha256(input) {
  return crypto.createHash('sha256').update(input, 'utf8').digest('hex');
}

function base64UrlNoPadding(buffer) {
  return Buffer.from(buffer).toString('base64').replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/g, '');
}

function base64UrlDecode(input) {
  const normalized = input.replaceAll('-', '+').replaceAll('_', '/');
  const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4);
  return Buffer.from(padded, 'base64');
}

function phoneNoCiphertext(phone) {
  const pem = ['-----BEGIN PUBLIC KEY-----', DEFAULT_PUBLIC_KEY, '-----END PUBLIC KEY-----'].join('\n');
  const encrypted = crypto.publicEncrypt(
    { key: pem, padding: crypto.constants.RSA_PKCS1_PADDING },
    Buffer.from(phone, 'utf8')
  );
  return base64UrlNoPadding(encrypted);
}

function deriveSignKey(accessToken, r2, r3) {
  const parts = String(accessToken || '').split('.');
  if (parts.length !== 3) throw new Error('accessToken 不是三段式 token，无法计算 signKey');
  const tokenTail = base64UrlDecode(parts[2]);
  const r2Buf = Buffer.from(r2 || '', 'base64');
  const r3Buf = Buffer.from(r3 || '', 'base64');
  const len = Math.min(tokenTail.length, r2Buf.length, r3Buf.length);
  const out = Buffer.alloc(len);
  for (let i = 0; i < len; i += 1) out[i] = tokenTail[i] ^ r2Buf[i] ^ r3Buf[i];
  return out;
}

function jwtPayloadSummary(token, state) {
  const parts = String(token || '').split('.');
  if (parts.length !== 3) return { validJwtShape: false };
  try {
    const payload = JSON.parse(base64UrlDecode(parts[1]).toString('utf8'));
    const userName = String(payload.user_name || payload.username || '');
    const summary = {
      validJwtShape: true,
      keys: Object.keys(payload).sort(),
      exp: payload.exp || null,
      iat: payload.iat || null,
      client_id: payload.client_id || '',
      scope: payload.scope || '',
      userNameContainsDeviceId: state.deviceId ? userName.includes(state.deviceId) : false,
      userNameContainsOldAccountId: state.oldAuth?.accountId ? userName.includes(String(state.oldAuth.accountId)) : false,
      userNameContainsNewAccountId: state.newAuth?.accountId ? userName.includes(String(state.newAuth.accountId)) : false
    };
    for (const key of ['accountId', 'account_id', 'sub', 'user_name', 'username']) {
      if (payload[key] !== undefined && payload[key] !== null) summary[key] = maskValue(payload[key]);
    }
    return summary;
  } catch {
    return { validJwtShape: true, payloadParseError: true };
  }
}

async function fetchJson(url, options = {}) {
  const { method = 'GET', headers = {}, query, jsonBody, formBody, queryPost = false } = options;
  const target = new URL(url);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null) target.searchParams.set(key, asString(value));
    }
  }
  const requestHeaders = { ...headers };
  let body;
  if (jsonBody !== undefined) {
    requestHeaders['content-type'] = 'application/json; charset=utf-8';
    body = JSON.stringify(jsonBody);
  } else if (formBody !== undefined) {
    requestHeaders['content-type'] = 'application/x-www-form-urlencoded; charset=utf-8';
    body = queryString(formBody);
  } else if (queryPost) {
    body = '';
  }
  const response = await fetch(target, { method, headers: requestHeaders, body });
  const text = await response.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = { raw: text };
  }
  if (!response.ok) {
    const err = new Error(`HTTP ${response.status} ${response.statusText}`);
    err.status = response.status;
    err.response = data;
    throw err;
  }
  return data;
}

async function fetchFirstJson(candidates) {
  const errors = [];
  for (const candidate of candidates) {
    try {
      return await fetchJson(candidate.url, candidate.options);
    } catch (error) {
      errors.push(`${candidate.label || candidate.url}: ${error.message}`);
    }
  }
  const err = new Error(errors.join(' | ') || '没有可尝试的请求地址');
  err.attempts = errors;
  throw err;
}

function queryString(params = {}) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null) search.set(key, asString(value));
  }
  return search.toString();
}

function extractOldAuth(loginResponse) {
  const direct = loginResponse?.data?.appLoginVO || loginResponse?.data?.appOneLoginVO;
  const auth = direct || findObject(loginResponse, (obj) => obj.accountId && obj.token);
  if (!auth?.accountId || !auth?.token) throw new Error('验证码登录响应中没有找到 accountId/token');
  const tokenExpired = auth.tokenExpired != null ? String(auth.tokenExpired) : '';
  const obtainedAt = Date.now();
  const tokenExpiresAt = tokenExpired && Number(tokenExpired) > 0
    ? obtainedAt + Number(tokenExpired) * 1000
    : 0;
  return {
    accountId: String(auth.accountId),
    token: String(auth.token),
    refreshToken: auth.refreshToken ? String(auth.refreshToken) : '',
    nickname: auth.nickname || '',
    tokenExpired,
    tokenObtainedAt: obtainedAt,
    tokenExpiresAt
  };
}

function getOldTokenExpiresAt(oldAuth) {
  if (!oldAuth) return 0;
  if (oldAuth.tokenExpiresAt) return Number(oldAuth.tokenExpiresAt) || 0;
  if (oldAuth.tokenObtainedAt && oldAuth.tokenExpired) {
    const seconds = Number(oldAuth.tokenExpired);
    if (seconds > 0) return Number(oldAuth.tokenObtainedAt) + seconds * 1000;
  }
  return 0;
}

function isOldAuthFailureResponse(response) {
  if (!response || typeof response !== 'object') return false;
  if (response.result === 39 || response.result === '39') return true;
  const message = String(response.message || response.msg || '');
  return /信息校验失败|第三方TOKEN失效|token.*失效|鉴权失败/i.test(message);
}

function isOldAuthFailureError(error) {
  return isOldAuthFailureResponse(error?.response) || /信息校验失败|第三方TOKEN失效/i.test(error?.message || '');
}

function summarizeRefreshError(body) {
  if (!body || typeof body !== 'object') return body || null;
  return {
    code: body.code ?? body.result ?? null,
    message: body.msg || body.message || null,
    success: body.success
  };
}

function extractNewAuth(exchangeResponse, oldAuth) {
  const data = exchangeResponse?.data || findObject(exchangeResponse, (obj) => obj.accessToken && obj.signParam);
  if (!data?.accessToken || !data?.signParam?.r2 || !data?.signParam?.r3) {
    throw new Error('新网关登录响应中没有找到 accessToken/signParam.r2/r3');
  }
  const accessToken = String(data.accessToken);
  return {
    accountId: String(oldAuth?.accountId || data.accountId || ''),
    gatewayAccountId: data.accountId ? String(data.accountId) : '',
    accessToken,
    refreshToken: data.refreshToken ? String(data.refreshToken) : '',
    tokenExpireTime: data.tokenExpireTime || '',
    accessTokenExpiresAt: jwtExpiryMs(accessToken) || expiryFromDuration(data.tokenExpireTime),
    nickname: data.nickname || '',
    signParam: data.signParam,
    encryptParam: data.encryptParam || null,
    signKey: deriveSignKey(data.accessToken, data.signParam.r2, data.signParam.r3)
  };
}

function getAccessTokenExpiresAt(auth) {
  if (!auth?.accessToken) return 0;
  return jwtExpiryMs(auth.accessToken) || Number(auth.accessTokenExpiresAt) || 0;
}

function jwtExpiryMs(token) {
  const parts = String(token || '').split('.');
  if (parts.length !== 3) return 0;
  try {
    const exp = Number(JSON.parse(base64UrlDecode(parts[1]).toString('utf8')).exp);
    return Number.isFinite(exp) && exp > 0 ? exp * 1000 : 0;
  } catch {
    return 0;
  }
}

function expiryFromDuration(value) {
  const seconds = Number(value);
  return Number.isFinite(seconds) && seconds > 0 ? Date.now() + seconds * 1000 : 0;
}

function isAuthFailureError(error) {
  return error?.status === 401 || error?.status === 403 || isAuthFailureResponse(error?.response);
}

function isAuthFailureResponse(data) {
  if (!data || typeof data !== 'object') return false;
  const code = String(data.code ?? data.result ?? data.status ?? '');
  if (code === '401' || code === '403') return true;
  const message = String(data.msg || data.message || data.error || '').toLowerCase();
  return /token|登录|鉴权|认证/.test(message) && /expire|expired|invalid|过期|失效|无效|重新登录/.test(message);
}

function sanitizeLoginResponse(raw) {
  return {
    code: raw?.code ?? raw?.result ?? raw?.status ?? null,
    success: raw?.success ?? raw?.result === 0,
    msg: raw?.msg || raw?.message || '',
    risk_type: raw?.data?.risk_type || raw?.risk_type || '',
    requestId: raw?.data?.requestId || raw?.requestId || '',
    hasAppLoginVO: Boolean(raw?.data?.appLoginVO || raw?.data?.appOneLoginVO)
  };
}

function vehicleListFromResponse(response) {
  const vehicles = collectObjects(response, (obj) => typeof obj.vin === 'string' && obj.vin.length >= 8);
  const byVin = new Map();
  for (const vehicle of vehicles) {
    if (!byVin.has(vehicle.vin)) byVin.set(vehicle.vin, vehicle);
  }
  return [...byVin.values()];
}

function routeFromResponse(response) {
  return response?.data || findObject(response, (obj) => obj.appRegion || obj.appCenter) || null;
}

function encryptOperationPassword(password, oldAuth) {
  if (!password) return '';
  if (!oldAuth?.token || oldAuth.token.length < 64) {
    throw new Error('旧 token 长度不足，无法按 App 规则加密操作密码');
  }
  const key = Buffer.from(shortMd5(oldAuth.token.slice(0, 32)), 'utf8');
  const iv = Buffer.from(shortMd5(oldAuth.token.slice(32, 64)), 'utf8');
  const cipher = crypto.createCipheriv('aes-128-cbc', key, iv);
  return Buffer.concat([cipher.update(password, 'utf8'), cipher.final()]).toString('base64');
}

function normalizeStateJson(value) {
  if (typeof value === 'object' && value !== null) return JSON.stringify(value);
  const text = String(value || '').trim();
  if (!text) throw new Error('state JSON 不能为空');
  JSON.parse(text);
  return text;
}

function redactControlBody(body) {
  const out = { ...body };
  if (out.oppwd) out.oppwd = `${String(out.oppwd).slice(0, 4)}...`;
  if (out.signStr) out.signStr = `${String(out.signStr).slice(0, 6)}...`;
  if (out.carvin) out.carvin = maskValue(out.carvin);
  return out;
}

function pickHost(value) {
  if (!value) return '';
  return String(value).replace(/\/+$/g, '');
}

function findObject(root, predicate, seen = new Set()) {
  if (!root || typeof root !== 'object' || seen.has(root)) return null;
  seen.add(root);
  if (predicate(root)) return root;
  const values = Array.isArray(root) ? root : Object.values(root);
  for (const value of values) {
    const found = findObject(value, predicate, seen);
    if (found) return found;
  }
  return null;
}

function collectObjects(root, predicate, seen = new Set(), out = []) {
  if (!root || typeof root !== 'object' || seen.has(root)) return out;
  seen.add(root);
  if (predicate(root)) out.push(root);
  const values = Array.isArray(root) ? root : Object.values(root);
  for (const value of values) collectObjects(value, predicate, seen, out);
  return out;
}

function maskValue(value) {
  const str = String(value || '');
  if (!str) return '';
  if (str.length <= 8) return `${str.slice(0, 2)}***`;
  return `${str.slice(0, 4)}...${str.slice(-4)}`;
}
