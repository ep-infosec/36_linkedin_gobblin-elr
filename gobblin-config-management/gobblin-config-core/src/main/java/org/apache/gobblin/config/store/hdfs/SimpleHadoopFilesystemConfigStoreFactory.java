/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.gobblin.config.store.hdfs;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.apache.gobblin.config.store.api.ConfigStoreCreationException;
import org.apache.gobblin.config.store.api.ConfigStoreFactory;
import org.apache.gobblin.util.ConfigUtils;


/**
 * An abstract base class for {@link ConfigStoreFactory}s based on {@link FileSystem}.
 * Subclasses should implement {@link #getPhysicalScheme()}, {@link #getDefaultStoreFs(Config, Optional)} and
 * {@link #getDefaultRootDir(Config, FileSystem, Optional)}.
 */
public abstract class SimpleHadoopFilesystemConfigStoreFactory implements ConfigStoreFactory<SimpleHadoopFilesystemConfigStore> {

  protected static final String SIMPLE_HDFS_SCHEME_PREFIX = "simple-";

  /** Global namespace for properties if no scope is used */
  public static final String DEFAULT_CONFIG_NAMESPACE = SimpleHDFSConfigStoreFactory.class.getName();
  /** Scoped configuration properties */
  public static final String DEFAULT_STORE_URI_KEY = "default_store_uri";

  private final String physicalScheme;
  private final Config factoryConfig;
  private final URI defaultURI;

  private Optional<FileSystem> defaultFileSystem;
  private Optional<URI> defaultRootDir;

  /** Instantiates a new instance using standard typesafe config defaults:
   * {@link ConfigFactory#load()} */
  public SimpleHadoopFilesystemConfigStoreFactory() {
    this(ConfigUtils.getConfigOrEmpty(ConfigFactory.load(), DEFAULT_CONFIG_NAMESPACE));
  }

  /**
   * Instantiates a new instance of the factory with the specified config. The configuration is
   * expected to be scoped, i.e. the properties should not be prefixed.
   */
  public SimpleHadoopFilesystemConfigStoreFactory(Config factoryConfig) {
    this.physicalScheme = getPhysicalScheme();
    this.factoryConfig = factoryConfig;
    this.defaultURI = computeDefaultURI(this.factoryConfig);
  }

  private URI computeDefaultURI(Config factoryConfig) {
    if (factoryConfig.hasPath(DEFAULT_STORE_URI_KEY)) {
      String uriString = factoryConfig.getString(DEFAULT_STORE_URI_KEY);
      if (Strings.isNullOrEmpty(uriString)) {
        throw new IllegalArgumentException("Default store URI should be non-empty");
      }
      try {
        URI uri = new URI(uriString);
        if (uri.getScheme() == null || this.physicalScheme.equals(uri.getScheme())) {
          return uri;
        }
      } catch (URISyntaxException use) {
        throw new IllegalArgumentException("Could not use default uri " + uriString);
      }
    }
    return null;
  }

  /**
   * Returns the physical scheme this {@link ConfigStoreFactory} is responsible for. To support new HDFS
   * {@link FileSystem} implementations, subclasses should override this method.
   */
  protected abstract String getPhysicalScheme();

  /**
   * Returns the default {@link FileSystem} used for {@link org.apache.gobblin.config.store.api.ConfigStore}s generated by this
   * factory.
   * @param factoryConfig the user supplied factory configuration.
   * @param configDefinedDefaultURI if the user specified a default uri, that uri.
   */
  protected abstract FileSystem getDefaultStoreFs(Config factoryConfig, Optional<URI> configDefinedDefaultURI);

  /**
   * Returns the {@link URI} for the default store created by this factory.
   * @param factoryConfig the user supplied factory configuration.
   * @param configDefinedDefaultURI if the user specified a default uri, that uri.
   * @param defaultFileSystem the default {@link FileSystem} obtained from {@link #getDefaultStoreFs(Config, Optional)}.
   */
  protected abstract URI getDefaultRootDir(Config factoryConfig, FileSystem defaultFileSystem, Optional<URI> configDefinedDefaultURI);

  private synchronized FileSystem getDefaultStoreFsLazy() {
    if (this.defaultFileSystem == null) {
      this.defaultFileSystem = Optional.fromNullable(getDefaultStoreFs(this.factoryConfig, Optional.fromNullable(this.defaultURI)));
    }
    return this.defaultFileSystem.orNull();
  }

  private synchronized URI getDefaultStoreURILazy() {
    if (this.defaultRootDir == null) {
      this.defaultRootDir = Optional.fromNullable(computeDefaultStoreURI());
    }
    return this.defaultRootDir.orNull();
  }

  private URI computeDefaultStoreURI() {
    try {
      if (getDefaultStoreFsLazy() == null) {
        return null;
      }

      URI defaultRoot = getDefaultRootDir(this.factoryConfig, getDefaultStoreFsLazy(), Optional.fromNullable(this.defaultURI));
      if (defaultRoot == null) {
        return null;
      }

      Path path = getDefaultStoreFsLazy().makeQualified(new Path(defaultRoot));
      if (!isValidStoreRootPath(getDefaultStoreFsLazy(), path)) {
        throw new IllegalArgumentException(path + " is not a config store.");
      }
      return path.toUri();
    } catch (IOException ioe) {
      throw new RuntimeException("Could not create a default uri for scheme " + getScheme(), ioe);
    }
  }

  private static boolean isValidStoreRootPath(FileSystem fs, Path storeRootPath) throws IOException {
    Path storeRoot = new Path(storeRootPath, SimpleHadoopFilesystemConfigStore.CONFIG_STORE_NAME);
    return fs.exists(storeRoot);
  }

  @Override
  public String getScheme() {
    return getSchemePrefix() + getPhysicalScheme();
  }

  /**
   * Creates a {@link SimpleHadoopFilesystemConfigStore} for the given {@link URI}. The {@link URI} specified should be the fully
   * qualified path to the dataset in question. For example,
   * {@code simple-hdfs://[authority]:[port][path-to-config-store][path-to-dataset]}. It is important to note that the
   * path to the config store on HDFS must also be specified. The combination
   * {@code [path-to-config-store][path-to-dataset]} need not specify an actual {@link Path} on HDFS.
   *
   * <p>
   *   If the {@link URI} does not contain an authority, a default authority and root directory are provided. The
   *   default authority is taken from the NameNode {@link URI} the current process is co-located with. The default path
   *   is "/user/[current-user]/".
   * </p>
   *
   * @param  configKey       The URI of the config key that needs to be accessed.
   *
   * @return a {@link SimpleHadoopFilesystemConfigStore} configured with the the given {@link URI}.
   *
   * @throws ConfigStoreCreationException if the {@link SimpleHadoopFilesystemConfigStore} could not be created.
   */
  @Override
  public SimpleHadoopFilesystemConfigStore createConfigStore(URI configKey) throws ConfigStoreCreationException {
    FileSystem fs = createFileSystem(configKey);
    URI physicalStoreRoot = getStoreRoot(fs, configKey);
    URI logicalStoreRoot = URI.create(getSchemePrefix() + physicalStoreRoot);
    return new SimpleHadoopFilesystemConfigStore(fs, physicalStoreRoot, logicalStoreRoot);
  }

  protected String getSchemePrefix() {
    return SIMPLE_HDFS_SCHEME_PREFIX;
  }

  /**
   * Creates a {@link FileSystem} given a user specified configKey.
   */
  private FileSystem createFileSystem(URI configKey) throws ConfigStoreCreationException {
    try {
      return FileSystem.get(createFileSystemURI(configKey), new Configuration());
    } catch (IOException | URISyntaxException e) {
      throw new ConfigStoreCreationException(configKey, e);
    }
  }

  /**
   * Creates a Hadoop FS {@link URI} given a user-specified configKey. If the given configKey does not have an authority,
   * a default one is used instead, provided by the default root path.
   */
  private URI createFileSystemURI(URI configKey) throws URISyntaxException, IOException {
    // Validate the scheme
    String configKeyScheme = configKey.getScheme();
    if (!configKeyScheme.startsWith(getSchemePrefix())) {
      throw new IllegalArgumentException(
          String.format("Scheme for configKey \"%s\" must begin with \"%s\"!", configKey, getSchemePrefix()));
    }

    if (Strings.isNullOrEmpty(configKey.getAuthority())) {
      return new URI(getPhysicalScheme(), getDefaultStoreFsLazy().getUri().getAuthority(), "", "", "");
    }
    String uriPhysicalScheme = configKeyScheme.substring(getSchemePrefix().length(), configKeyScheme.length());
    return new URI(uriPhysicalScheme, configKey.getAuthority(), "", "", "");
  }

  /**
   * This method determines the physical location of the {@link SimpleHadoopFilesystemConfigStore} root directory on HDFS. It does
   * this by taking the {@link URI} given by the user and back-tracing the path. It checks if each parent directory
   * contains the folder {@link SimpleHadoopFilesystemConfigStore#CONFIG_STORE_NAME}. It the assumes this {@link Path} is the root
   * directory.
   *
   * <p>
   *   If the given configKey does not have an authority, then this method assumes the given {@link URI#getPath()} does
   *   not contain the dataset root. In which case it uses the {@link #getDefaultRootDir()} as the root directory. If
   *   the default root dir does not contain the {@link SimpleHadoopFilesystemConfigStore#CONFIG_STORE_NAME} then a
   *   {@link ConfigStoreCreationException} is thrown.
   * </p>
   */
  private URI getStoreRoot(FileSystem fs, URI configKey) throws ConfigStoreCreationException {
    if (Strings.isNullOrEmpty(configKey.getAuthority())) {
      if (getDefaultStoreURILazy() != null) {
        return getDefaultStoreURILazy();
      } else if (isAuthorityRequired()) {
        throw new ConfigStoreCreationException(configKey, "No default store has been configured.");
      }
    }

    Path path = new Path(configKey.getPath());

    while (path != null) {
      try {
        // the abs URI may point to an unexist path for
        // 1. phantom node
        // 2. as URI did not specify the version
        if (fs.exists(path)) {
          for (FileStatus fileStatus : fs.listStatus(path)) {
            if (fileStatus.isDirectory()
                && fileStatus.getPath().getName().equals(SimpleHadoopFilesystemConfigStore.CONFIG_STORE_NAME)) {
              return fs.getUri().resolve(fileStatus.getPath().getParent().toUri());
            }
          }
        }
      } catch (IOException e) {
        throw new ConfigStoreCreationException(configKey, e);
      }

      path = path.getParent();
    }
    throw new ConfigStoreCreationException(configKey, "Cannot find the store root!");
  }

  protected boolean isAuthorityRequired() {
    return true;
  }

  @VisibleForTesting
  URI getDefaultStoreURI() {
    return getDefaultStoreURILazy() == null ? null : getDefaultStoreURILazy();
  }
}

