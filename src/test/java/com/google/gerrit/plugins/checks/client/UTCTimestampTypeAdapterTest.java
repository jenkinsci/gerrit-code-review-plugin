// Copyright (C) 2026 NVIDIA Corporation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.plugins.checks.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.TimeZone;
import org.junit.Test;

public class UTCTimestampTypeAdapterTest {
  private final Gson gson =
      new GsonBuilder()
          .registerTypeAdapter(Timestamp.class, new UTCTimestampTypeAdapter())
          .create();

  @Test
  public void readsThreeAndNineDigitFractionsAsTheSameInstant() {
    Timestamp expected = Timestamp.from(Instant.parse("2019-01-31T09:59:32.126Z"));

    assertEquals(expected, gson.fromJson("\"2019-01-31 09:59:32.126\"", Timestamp.class));
    assertEquals(expected, gson.fromJson("\"2019-01-31 09:59:32.126000000\"", Timestamp.class));
  }

  @Test
  public void acceptsNineDigitFractionWhenReadingGerritResponse() {
    Timestamp expected = Timestamp.from(Instant.parse("2019-01-31T09:59:32.126789123Z"));

    assertEquals(expected, gson.fromJson("\"2019-01-31 09:59:32.126789123\"", Timestamp.class));
  }

  @Test
  public void preservesSubMillisecondFractionWhenReadingGerritResponse() {
    Timestamp expected = Timestamp.from(Instant.parse("2019-01-31T09:59:32.000000001Z"));

    assertEquals(expected, gson.fromJson("\"2019-01-31 09:59:32.000000001\"", Timestamp.class));
  }

  @Test
  public void keepsMillisecondWidthRequestContract() {
    Timestamp value = Timestamp.from(Instant.parse("2019-01-31T09:59:32.126789123Z"));

    assertEquals("\"2019-01-31 09:59:32.126\"", gson.toJson(value, Timestamp.class));
  }

  @Test
  public void readsAndWritesJsonNull() {
    assertNull(gson.fromJson("null", Timestamp.class));
    assertEquals("null", gson.toJson(null, Timestamp.class));
  }

  @Test
  public void treatsGerritTimestampAsUtcRegardlessOfDefaultTimeZone() {
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
      Timestamp expected = Timestamp.from(Instant.parse("2019-01-31T09:59:32.126Z"));

      assertEquals(expected, gson.fromJson("\"2019-01-31 09:59:32.126\"", Timestamp.class));
      assertEquals("\"2019-01-31 09:59:32.126\"", gson.toJson(expected, Timestamp.class));
    } finally {
      TimeZone.setDefault(original);
    }
  }

  @Test
  public void rejectsMalformedTimestamp() {
    try {
      gson.fromJson("\"not-a-timestamp\"", Timestamp.class);
      fail("Expected malformed timestamp to be rejected");
    } catch (JsonParseException expected) {
      // Expected.
    }
  }
}
