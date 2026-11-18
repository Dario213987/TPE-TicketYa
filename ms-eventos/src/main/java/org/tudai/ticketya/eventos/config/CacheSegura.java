package org.tudai.ticketya.eventos.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;

/**
 * Envuelve una cache para que un Redis caído nunca rompa una llamada (TKY-81/82): cada error se
 * registra como WARN y se sigue como si la cache estuviera vacía.
 * <p>
 * Hace falta además del {@code CacheErrorHandler} porque las escrituras e invalidaciones
 * transaccionales se ejecutan después del commit, fuera del alcance de ese handler.
 */
@Slf4j
class CacheSegura implements Cache {

    private final Cache cache;

    CacheSegura(Cache cache) {
        this.cache = cache;
    }

    @Override
    public String getName() {
        return cache.getName();
    }

    @Override
    public Object getNativeCache() {
        return cache.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        try {
            return cache.get(key);
        } catch (RuntimeException e) {
            aviso("leer", key, e);
            return null;
        }
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        try {
            return cache.get(key, type);
        } catch (RuntimeException e) {
            aviso("leer", key, e);
            return null;
        }
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        try {
            return cache.get(key, valueLoader);
        } catch (Cache.ValueRetrievalException e) {
            throw e;
        } catch (RuntimeException e) {
            aviso("leer", key, e);
            try {
                return valueLoader.call();
            } catch (Exception carga) {
                throw new Cache.ValueRetrievalException(key, valueLoader, carga);
            }
        }
    }

    @Override
    public void put(Object key, Object value) {
        try {
            cache.put(key, value);
        } catch (RuntimeException e) {
            aviso("guardar", key, e);
        }
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        try {
            return cache.putIfAbsent(key, value);
        } catch (RuntimeException e) {
            aviso("guardar", key, e);
            return null;
        }
    }

    @Override
    public void evict(Object key) {
        try {
            cache.evict(key);
        } catch (RuntimeException e) {
            aviso("invalidar", key, e);
        }
    }

    @Override
    public boolean evictIfPresent(Object key) {
        try {
            return cache.evictIfPresent(key);
        } catch (RuntimeException e) {
            aviso("invalidar", key, e);
            return false;
        }
    }

    @Override
    public void clear() {
        try {
            cache.clear();
        } catch (RuntimeException e) {
            aviso("vaciar", "*", e);
        }
    }

    @Override
    public boolean invalidate() {
        try {
            return cache.invalidate();
        } catch (RuntimeException e) {
            aviso("vaciar", "*", e);
            return false;
        }
    }

    private void aviso(String operacion, Object key, RuntimeException e) {
        log.warn("Cache {} no disponible al {} {}; se sigue con la base: {}", getName(), operacion, key, e.getMessage());
    }
}
