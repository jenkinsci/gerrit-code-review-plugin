package com.google.gerrit.plugins.checks.client;

import static java.time.ZoneOffset.UTC;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;

/**
 * Format {@link java.sql.Timestamp} objects to JSON string representation compatible with the
 * Gerrit API.
 */
class UTCTimestampTypeAdapter extends TypeAdapter<Timestamp> {
  private static final DateTimeFormatter UTC_MILLISECOND_FORMAT =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS", Locale.US).withZone(UTC);
  private static final DateTimeFormatter UTC_RESPONSE_FORMAT =
      new DateTimeFormatterBuilder()
          .appendPattern("uuuu-MM-dd HH:mm:ss")
          .appendFraction(ChronoField.NANO_OF_SECOND, 3, 9, true)
          .toFormatter(Locale.US)
          .withResolverStyle(ResolverStyle.STRICT);

  public UTCTimestampTypeAdapter() {}

  @Override
  public void write(JsonWriter out, Timestamp date) throws IOException {
    if (date == null) {
      out.nullValue();
    } else {
      // Keep the established request contract: Gerrit accepts millisecond-width fractions.
      out.value(UTC_MILLISECOND_FORMAT.format(date.toInstant()));
    }
  }

  @Override
  public Timestamp read(JsonReader in) throws IOException {
    if (in.peek() == JsonToken.NULL) {
      in.nextNull();
      return null;
    }

    try {
      // Gerrit pads millisecond precision to nine fractional digits. Parse the field as a fraction
      // of one second; SimpleDateFormat would incorrectly treat all digits as milliseconds.
      LocalDateTime value = LocalDateTime.parse(in.nextString(), UTC_RESPONSE_FORMAT);
      return Timestamp.from(value.toInstant(UTC));
    } catch (DateTimeParseException e) {
      throw new JsonParseException(e);
    }
  }
}
