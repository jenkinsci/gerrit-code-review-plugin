package com.google.gerrit.plugins.checks.client;

import static java.time.ZoneOffset.UTC;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Format {@link java.sql.Timestamp} objects to JSON string representation compatible with the
 * Gerrit API.
 */
class UTCTimestampTypeAdapter extends TypeAdapter<Date> {
  private final DateFormat utcDateFormat;

  public UTCTimestampTypeAdapter() {
    super();
    utcDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    utcDateFormat.setTimeZone(TimeZone.getTimeZone(UTC));
  }

  @Override
  public void write(JsonWriter out, Date date) throws IOException {
    if (date == null) {
      out.nullValue();
    } else {
      out.value(utcDateFormat.format(date));
    }
  }

  @Override
  public Date read(JsonReader in) throws IOException {
    if (in.peek() == JsonToken.NULL) {
      in.nextNull();
      return null;
    }

    try {
      return utcDateFormat.parse(in.nextString());
    } catch (ParseException e) {
      throw new JsonParseException(e);
    }
  }
}
