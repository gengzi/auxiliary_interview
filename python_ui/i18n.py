import os


class I18n:
    def __init__(self, base_dir, locale="zh_CN"):
        self._base_dir = base_dir
        self._bundles = {}
        self._locale = locale
        self._load_bundle("en_US")
        self._load_bundle("zh_CN")

    def set_locale(self, locale):
        if locale and locale != self._locale:
            self._locale = locale

    def get_locale(self):
        return self._locale

    def tr(self, key, *args):
        bundle = self._bundles.get(self._locale, {})
        pattern = bundle.get(key) or self._bundles.get("en_US", {}).get(key, key)
        if args:
            try:
                return pattern.format(*args)
            except Exception:
                return pattern
        return pattern

    def _load_bundle(self, locale):
        path = os.path.join(self._base_dir, "i18n", f"messages_{locale}.properties")
        self._bundles[locale] = _read_properties(path)


def _read_properties(path):
    props = {}
    if not os.path.exists(path):
        return props
    with open(path, "r", encoding="utf-8") as handle:
        for raw in handle:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if "=" not in line:
                continue
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props
