package com.aversion.server.modules;

import com.aversion.server.AversionServer;
import com.aversion.server.utils.ReflectionUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Module manager handles loading, initializing, and managing MCP modules.
 * <p>
 * Provides centralized management of all modules in the MCP server.
 */
public class ModuleManager {
  private static final com.aversion.server.utils.Logger logger = com.aversion.server.utils.Logger.getInstance();

  private final AversionServer server;
  private final Map<String, BaseModule> modules = new ConcurrentHashMap<>();

  public ModuleManager(AversionServer server) {
    this.server = server;
  }

  /**
   * Discovers and registers all known modules found in the classpath.
   * Modules are discovered using {@link ReflectionUtil#getModules()}.
   */
  public void registerKnownModules() {
    Set<? extends BaseModule> knownModules = ReflectionUtil.getModules();
    if (knownModules.isEmpty()) {
      logger.warn("No known modules found in the classpath");
      return;
    }
    logger.info("Found {} known modules in the classpath", knownModules.size());
    registerModules(knownModules.toArray(BaseModule[]::new));
  }

  /**
   * Register a module with the manager and initialize it.
   *
   * @param module The module instance to register and initialize
   * @throws RuntimeException if module name conflicts or initialization fails
   */
  public void registerModule(BaseModule module) {
    BaseModule.ModuleConfig config = module.getConfig();

    if (modules.containsKey(config.name())) {
      throw new RuntimeException("Module '" + config.name() + "' is already registered");
    }

    try {
      module.initialize(server);
      modules.put(config.name(), module);
    } catch (Exception error) {
      throw new RuntimeException("Failed to initialize module '" + config.name() + "'", error);
    }
  }

  /**
   * Register multiple modules at once.
   *
   * @param modules Array of module instances to register
   * @throws RuntimeException if any module registration fails (stops at first failure)
   */
  public void registerModules(BaseModule... modules) {
    Arrays.stream(modules).forEach(this::registerModule);
  }

  /**
   * Get a registered module by its name.
   *
   * @param name The unique name of the module to retrieve
   * @return The module instance if found, null otherwise
   */
  public BaseModule getModule(String name) {
    return modules.get(name);
  }

  /**
   * Get all registered modules.
   *
   * @return List containing all registered module instances
   */
  public List<BaseModule> getAllModules() {
    return List.copyOf(modules.values());
  }

  /**
   * Get configuration information for all registered modules.
   *
   * @return List of module configuration objects
   */
  public List<BaseModule.ModuleConfig> getModuleInfo() {
    return modules.values().stream()
      .map(BaseModule::getConfig)
      .toList();
  }

  /**
   * Check if a module with the specified name is registered.
   *
   * @param name The module name to check for
   * @return True if the module is registered, false otherwise
   */
  public boolean hasModule(String name) {
    return modules.containsKey(name);
  }

  /**
   * Get the total number of registered modules.
   *
   * @return The count of currently registered modules
   */
  public int getModuleCount() {
    return modules.size();
  }

  /**
   * Unregister a module from the manager.
   *
   * @param name The name of the module to unregister
   * @return True if the module was found and removed, false if not found
   * @warning This only removes the module from the manager; tools remain registered with the MCP server
   */
  public boolean unregisterModule(String name) {
    BaseModule module = modules.get(name);
    if (module == null) {
      return false;
    }

    try {
      module.onUnload();
      modules.remove(name);
      logger.info("Module unregistered: {}", name);
      return true;
    } catch (Exception error) {
      logger.error("Error unregistering module '{}'", name, error);
      return false;
    }
  }

  /**
   * Clear all modules from the manager.
   *
   * @warning This only removes modules from the manager; tools remain registered with the Aversion server
   */
  public void clear() {
    List<String> moduleNames = List.copyOf(modules.keySet());
    moduleNames.forEach(this::unregisterModule);
  }

}
