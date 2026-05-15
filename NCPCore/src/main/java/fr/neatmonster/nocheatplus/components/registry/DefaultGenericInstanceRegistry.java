/*
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.neatmonster.nocheatplus.components.registry;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import fr.neatmonster.nocheatplus.components.registry.event.GenericInstanceHandle.ReferenceCountHandle;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceRegistryListener;
import fr.neatmonster.nocheatplus.components.registry.event.IUnregisterGenericInstanceRegistryListener;
import fr.neatmonster.nocheatplus.components.registry.exception.RegistrationLockedException;
import fr.neatmonster.nocheatplus.logging.details.IGetStreamId;
import fr.neatmonster.nocheatplus.logging.details.ILogString;
import fr.neatmonster.nocheatplus.utilities.ds.map.HashMapLOW;

/**
 * Default/simple implementation. Handles are kept forever once fetched, for
 * simplicity and to avoid unnecessary onReload implementations.
 * 
 * @author asofold
 *
 */
public class DefaultGenericInstanceRegistry implements GenericInstanceRegistry, IUnregisterGenericInstanceRegistryListener {

    // TODO: Test cases.

    /**
     * Hold registration information for a class, as well as some convenience
     * functionality.
     * 
     * @author asofold
     *
     */
    public static class Registration<T> {

        private static final long DENY_OVERRIDE_INSTANCE = 0x01;
        private static final long DENY_REMOVE_INSTANCE = 0x02;

        // TODO: unique handles + use

        private final GenericInstanceRegistry registry;
        private final Lock lock;
        private final IUnregisterGenericInstanceRegistryListener unregister;
        private final Class<T> registeredFor;
        private T instance = null;
        /** Always kept registered, thus the reference count is ignored. */
        private ReferenceCountHandle<T> uniqueHandle = null;

        private long accessFlags = 0L;

        private final List<IGenericInstanceRegistryListener<T>> listeners = new LinkedList<IGenericInstanceRegistryListener<T>>();

        public Registration(Class<T> registeredFor, T instance,
                GenericInstanceRegistry registry, 
                IUnregisterGenericInstanceRegistryListener unregister,
                Lock lock) {
            this.registry = registry;
            this.unregister = unregister;
            this.registeredFor = registeredFor;
            this.instance = instance;
            this.lock = lock;
        }

        private void setFlag(long flag) {
            lock.lock();
            accessFlags |= flag;
            lock.unlock();
        }

        public void denyOverrideInstance() {
            setFlag(DENY_OVERRIDE_INSTANCE);
        }

        public void denyRemoveInstance() {
            setFlag(DENY_REMOVE_INSTANCE);
        }

        /**
         * Call for unregistering this instance officially. Listeners and
         * handles may be kept,
         * 
         * @return The previously registered instance.
         */
        public T unregisterInstance() {
            lock.lock();
            if ((accessFlags & DENY_REMOVE_INSTANCE) != 0) {
                lock.unlock();
                throw new RegistrationLockedException();
            }
            T oldInstance = this.instance;
            this.instance = null;
            if (!listeners.isEmpty()) {
                for (IGenericInstanceRegistryListener<T> listener : listeners) {
                    ((IGenericInstanceRegistryListener<T>) listener).onGenericInstanceRemove(registeredFor, oldInstance);
                }
            }
            lock.unlock();
            return oldInstance;
        }

        /**
         * Call on register.
         * 
         * @param instance
         *            The previously registered instance.
         * @return
         */
        public T registerInstance(T instance) {
            lock.lock();
            if ((accessFlags & DENY_OVERRIDE_INSTANCE) != 0) {
                lock.unlock();
                throw new RegistrationLockedException();
            }
            T oldInstance = this.instance;
            this.instance = instance;
            if (!listeners.isEmpty()) {
                if (oldInstance == null) {
                    for (IGenericInstanceRegistryListener<T> listener : listeners) {
                        listener.onGenericInstanceOverride(registeredFor, instance, oldInstance);
                    }
                }
                else {
                    for (IGenericInstanceRegistryListener<T> listener : listeners) {
                        listener.onGenericInstanceRegister(registeredFor, instance);
                    }
                }
            }
            lock.unlock();
            return oldInstance;
        }

        public IGenericInstanceHandle<T> getHandle() {
            lock.lock();
            if (uniqueHandle == null || uniqueHandle.isDisabled()) {
                updateUniqueHandle();
            }
            IGenericInstanceHandle<T> handle = uniqueHandle.getNewHandle();
            lock.unlock();
            return handle;
        }

        /**
         * Only call if uniqueHandle is null and under lock.
         */
        private void updateUniqueHandle() {
            if (uniqueHandle != null && uniqueHandle.isDisabled()) {
                unregisterListener(uniqueHandle);
            }
            if (uniqueHandle == null) {
                uniqueHandle = new ReferenceCountHandle<T>(registeredFor, registry, unregister);
                this.listeners.add(uniqueHandle);
                uniqueHandle.getHandle(); // Keep the count up, so this never unregisters automatically.
            }
        }

        public void unregisterListener(IGenericInstanceRegistryListener<T> listener) {
            lock.lock();
            IGenericInstanceHandle<T> disable = null;
            if (listener == uniqueHandle) {
                disable = uniqueHandle;
                uniqueHandle = null;
            }
            this.listeners.remove(listener);
            if (disable != null) {
                disable.disableHandle();
            }
            lock.unlock();
        }

        public T getInstance() {
            return (T) instance;
        }

        /**
         * Must be called under lock.
         * 
         * @return
         */
        private boolean canBeRemoved() {
            return instance == null || uniqueHandle == null && listeners.isEmpty();
        }

        /**
         * Call under lock only.
         */
        public void clear() {
            instance = null;
            if (uniqueHandle != null) {
                uniqueHandle.disableHandle();
                uniqueHandle = null;
            }
            listeners.clear();
        }

    }


    private final Lock lock = new ReentrantLock();
    private final HashMapLOW<Class<?>, Registration<?>> registrations = new HashMapLOW<Class<?>, Registration<?>>(lock,30);

    private ILogString logger = null;

    private IGetStreamId selectStream;

    private String logPrefix;

    public void setLogger(ILogString logger, IGetStreamId selectStream, String logPrefix) {
        this.logger = logger;
        this.selectStream = selectStream;
        this.logPrefix = logPrefix;
    }

    @SuppressWarnings("unchecked")
    private <T> Registration<T> getRegistration(Class<T> registeredFor, boolean create) {
        Registration<T> registration = (Registration<T>) registrations.get(registeredFor);
        if (registration == null && create) {
            // Create empty.
            return createEmptyRegistration(registeredFor);
        }
        else {
            return registration;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Registration<T> createEmptyRegistration(Class<T> registeredFor) {
        lock.lock();
        try {
            Registration<T> registration = (Registration<T>) registrations.get(registeredFor); // Re-check.
            if (registration == null) {
                // TODO: Consider individual locks / configuration for it.
                registration = new Registration<T>(registeredFor, null, this, this, lock);
                this.registrations.put(registeredFor, registration);
            }
            return registration;
        }
        catch (Throwable t) {
            throw new IllegalArgumentException(t); // Might document.
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public <T> void unregisterGenericInstanceRegistryListener(Class<T> registeredFor, IGenericInstanceRegistryListener<T> listener) {
        lock.lock();
        try {
            // (Include getRegistration, as no object creation is involved.)
            Registration<T> registration = getRegistration(registeredFor, false);
            if (registration != null) {
                registration.unregisterListener(listener);
            }
        }
        finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T registerGenericInstance(T instance) {
        return registerGenericInstance((Class<T>) instance.getClass(), instance);
    }

    @Override
    public <T, TI extends T> T registerGenericInstance(Class<T> registerFor, TI instance) {
        Registration<T> registration = getRegistration(registerFor, true); // Locks / throws.
        T previouslyRegistered = registration.registerInstance(instance);
        if (logger != null) {
            String msg = previouslyRegistered == null ? "Registered for " : "Registered (override) for ";
            String registerForName = registerFor.getName();
            String instanceName = instance.getClass().getName();
            if (registerForName.equals(instanceName)) {
                msg += "itself: " + instanceName;
            }
            else {
                msg += registerForName + ": " + instanceName;
            }
            logRegistryEvent(msg);
        }
        return previouslyRegistered;
    }

    @Override
    public <T> T getGenericInstance(Class<T> registeredFor) {
        Registration<T> registration = getRegistration(registeredFor, false);
        return registration == null ? null : registration.getInstance();
    }

    @Override
    public <T> T unregisterGenericInstance(Class<T> registeredFor) {
        lock.lock();
        Registration<T> registration = getRegistration(registeredFor, false);
        T registered = registration == null ? null : registration.unregisterInstance();
        // Repeat getting for removal test.
        if (registrations.containsKey(registeredFor) && getRegistration(registeredFor, false).canBeRemoved()) {
            registrations.remove(registeredFor);
        }
        lock.unlock();
        if (logger != null) {
            if (registered != null) {
                logRegistryEvent("Unregister, removed mapping for: " + registeredFor.getName());
            }
            else {
                logRegistryEvent("Unregister, no mapping present for: " + registeredFor.getName());
            }
        }
        return registered;
    }

    @Override
    public <T> IGenericInstanceHandle<T> getGenericInstanceHandle(Class<T> registeredFor) {
        return getRegistration(registeredFor, true).getHandle();
    }

    public void clear() {
        // TODO: consider fire unregister or add a removal method ?
        lock.lock();
        final Iterator<Entry<Class<?>, Registration<?>>> it = registrations.iterator();
        while (it.hasNext()) {
            it.next().getValue().clear();
        }
        registrations.clear();
        logRegistryEvent("Registry cleared.");
        lock.unlock();
    }

    /**
     * Convenience method to lock a registration vs. changing.
     * 
     * @param registeredFor
     */
    public void denyChangeExistingRegistration(Class<?> registeredFor) {
        Registration<?> registration = this.getRegistration(registeredFor, false);
        if (registration != null) {
            registration.denyOverrideInstance();
            registration.denyRemoveInstance();
        }
    }

    protected void logRegistryEvent(String message) {
        if (logger != null) {
            logger.info(selectStream.getStreamId(), logPrefix == null ? message : logPrefix + message);
        }
    }

}
