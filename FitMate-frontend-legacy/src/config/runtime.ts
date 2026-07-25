const LOCAL_API_BASE = 'http://127.0.0.1:7070';
const LOCAL_HOSTNAMES = ['127.0.0.1', 'localhost'];

function getWindowObject() {
  if (typeof window === 'undefined') {
    return undefined;
  }

  return window;
}

export function resolveApiBase(locationLike) {
  const hostname = locationLike && typeof locationLike.hostname === 'string'
    ? locationLike.hostname
    : '';

  return LOCAL_HOSTNAMES.indexOf(hostname) >= 0 ? LOCAL_API_BASE : '';
}

export function getApiBase() {
  const windowObject = getWindowObject();
  return resolveApiBase(windowObject && windowObject.location);
}

export const API_BASE = getApiBase();

export const runtimeConfig = Object.freeze({
  API_BASE,
  apiBase: API_BASE,
  localApiBase: LOCAL_API_BASE,
  isLocalDevelopmentHost: API_BASE === LOCAL_API_BASE
});

const windowObject = getWindowObject();
if (windowObject) {
  windowObject.API_BASE = API_BASE;
}

export default runtimeConfig;
