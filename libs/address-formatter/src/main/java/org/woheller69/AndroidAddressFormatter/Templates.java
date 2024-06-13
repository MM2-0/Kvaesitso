package org.woheller69.AndroidAddressFormatter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;

enum Templates {
  WORLDWIDE("worldwide.json"),
  COUNTRY_NAMES("countrynames.json"),
  ALIASES("aliases.json"),
  ABBREVIATIONS("abbreviations.json"),
  COUNTRY_2_LANG("country2lang.json"),
  COUNTY_CODES("countycodes.json"),
  STATE_CODES("statecodes.json")
;

  private interface Constants {
    ObjectMapper jsonWriter = new ObjectMapper();
  }

  private final JsonNode data;

  Templates(String fileName) {
    this.data = setData(fileName);
  }

  synchronized public JsonNode getData() {
    return this.data;
  }

  synchronized private static JsonNode setData(String fileName) {
    JsonNode node = null;
    try {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();

      InputStream is = cl.getResourceAsStream(fileName);
      node = Constants.jsonWriter.readTree(is);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return node;
  }
}
