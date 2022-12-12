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
package org.apache.gobblin.crypto;

import org.testng.Assert;
import org.testng.annotations.Test;


/**
 * Tests for the various KeyToStringCodec implementations.
 */
public class KeyToStringCodecTest {
  @Test
  public void testHexKeyToStringCodec() {
    String hexKey = "1234";
    byte[] binKey = new byte[]{18, 52};

    HexKeyToStringCodec codec = new HexKeyToStringCodec();
    Assert.assertEquals(codec.decodeKey(hexKey), binKey);
    Assert.assertEquals(codec.encodeKey(binKey), hexKey);
  }

  @Test
  public void testBase64KeyToStringCodec() {
    String b64Key = "EjQ=";
    byte[] binKey = new byte[]{18, 52};

    Base64KeyToStringCodec codec = new Base64KeyToStringCodec();
    Assert.assertEquals(codec.decodeKey(b64Key), binKey);
    Assert.assertEquals(codec.encodeKey(binKey), b64Key);
  }
}
