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

package org.apache.gobblin.runtime.cli;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Specify additional information to use when building a CLI option from a method.
 * This will only be respected on public methods with none or exactly one {@link String} parameter.
 */
@Retention(value= RetentionPolicy.RUNTIME) @Target(value= {ElementType.METHOD})
public @interface CliObjectOption {
  /**
   * The name of the option in cli (e.g. if name="myName", then CLI users would call "-myName" to activate the option).
   */
  String name() default "";

  /**
   * A description for the option.
   */
  String description() default "";
}
