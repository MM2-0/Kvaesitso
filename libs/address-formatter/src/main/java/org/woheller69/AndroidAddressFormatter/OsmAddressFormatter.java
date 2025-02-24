package org.woheller69.AndroidAddressFormatter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.mustachejava.*;
import java.io.IOException;
import java.io.StringWriter;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.Optional;
import java.util.ArrayList;

public class OsmAddressFormatter {

  private static final RegexPatternCache regexPatternCache = new RegexPatternCache();
  private static final List<String> knownComponents = OsmAddressFormatter.getKnownComponents();
  private static final Map<String, String> replacements = new HashMap<String, String>() {{
    put("[\\},\\s]+$", "");
    put("^[,\\s]+", "");
    put("^- ", "");
    put(",\\s*,", ", ");
    put("[ \t]+,[ \t]+", ", ");
    put("[ \t][ \t]+", " ");
    put("[ \t]\n", "\n");
    put("\n,", "\n");
    put(",+", ",");
    put(",\n", "\n");
    put("\n[ \t]+", "\n");
    put("\n+", "\n");
  }};

  private final ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
  private final boolean abbreviate;
  private final boolean appendCountry;
  private final boolean appendUnknown;

  public OsmAddressFormatter(Boolean abbreviate, Boolean appendCountry, Boolean appendUnknown) {
    this.abbreviate = abbreviate;
    this.appendCountry = appendCountry;
    this.appendUnknown = appendUnknown;
  }

  public String format(String json) throws IOException {
    return format(json, null);
  }

  public String format(String json, String fallbackCountryCode) throws IOException {
    TypeFactory factory = TypeFactory.defaultInstance();
    MapType type = factory.constructMapType(HashMap.class, String.class, String.class);
    Map<String, Object> components = null;

    try {
       components = yamlReader.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw(new IOException("Json processing exception", e));
    }
    components = normalizeFields(components);

    if (fallbackCountryCode != null) {
      components.put("country_code", fallbackCountryCode);
    }

    components = determineCountryCode(components, fallbackCountryCode);
    String countryCode = components.get("country_code").toString();

    if (appendCountry) {
      if (Templates.COUNTRY_NAMES.getData().has(countryCode) && components.get("country") == null) {
        components.put("country", Templates.COUNTRY_NAMES.getData().get(countryCode).asText());
      }
    }else components.remove("country");       //if !appendCountry do not show it, even if it is there

    components = applyAliases(components);
    JsonNode template = findTemplate(components);
    components = cleanupInput(components, template.get("replace"));
    return renderTemplate(template, components);
  }

  Map<String, Object> normalizeFields(Map<String, Object> components) {
    Map<String, Object> normalizedComponents = new HashMap<>();
    for (Map.Entry<String, Object> entry : components.entrySet()) {
      String field = entry.getKey();
      Object value = entry.getValue();
      String newField = lowerCamelToUnderscore(field);
      if (!normalizedComponents.containsKey(newField)) {
        normalizedComponents.put(newField, value);
      }
    }
    return normalizedComponents;
  }

  private String lowerCamelToUnderscore(String input) {
    Pattern pattern = Pattern.compile("([a-z])([A-Z])");
    Matcher matcher = pattern.matcher(input);
    String result = matcher.replaceAll("$1_$2");
    return result.toLowerCase();
  }

  Map<String, Object> determineCountryCode(Map<String, Object> components,
    String fallbackCountryCode) {
    String countryCode;

    if (components.get("country_code") != null) {
      countryCode = (String) components.get("country_code");
    } else if (fallbackCountryCode != null) {
      countryCode = fallbackCountryCode;
    } else {
      throw new Error("No country code provided. Use fallbackCountryCode?");
    }

    countryCode = countryCode.toUpperCase();

    if (!Templates.WORLDWIDE.getData().has(countryCode) || countryCode.length() != 2) {
      throw new Error("Invalid country code");
    }

    if (countryCode.equals("UK")) {
      countryCode = "GB";
    }

    JsonNode country = Templates.WORLDWIDE.getData().get(countryCode);
    if (country != null && country.has("use_country")) {
      String oldCountryCode = countryCode;
      countryCode = country.get("use_country").asText().toUpperCase();

      if (country.has("change_country")) {
        String newCountry = country.get("change_country").asText();
        Pattern p = regexPatternCache.get("\\$(\\w*)");
        Matcher m = p.matcher(newCountry);
        String match;
        if (m.find()) {
          match = m.group(1); // $state
          Pattern p2 = regexPatternCache.get(String.format("\\$%s", match));
          Matcher m2;
          if (components.get(match) != null && components.containsKey(match)) {
            m2 = p2.matcher(newCountry);
            String toReplace = components.get(match).toString();
            newCountry = m2.replaceAll(toReplace);
          } else {
            m2 = p2.matcher(newCountry);
            newCountry = m2.replaceAll("");
          }
          components.put("country", newCountry);
        }
        components.put("country", newCountry);
      }

      JsonNode oldCountry = Templates.WORLDWIDE.getData().get(oldCountryCode);
      JsonNode oldCountryAddComponent = oldCountry.get("add_component");
      if (oldCountryAddComponent != null && oldCountryAddComponent.toString().contains("=")) {
        String[] pairs = oldCountryAddComponent.textValue().split("=");
        if (pairs[0].equals("state")) {
          components.put("state", pairs[1]);
        }
      }
    }

    String state = (components.get("state") != null) ? components.get("state").toString() : null;

    if (countryCode.equals("NL") && state != null) {
      Pattern p1 = regexPatternCache.get("sint maarten", Pattern.CASE_INSENSITIVE);
      Matcher m1 = p1.matcher(state);
      Pattern p2 = regexPatternCache.get("aruba", Pattern.CASE_INSENSITIVE);
      Matcher m2 = p2.matcher(state);
      if (state.equals("Curaçao")) {
        countryCode = "CW";
        components.put("country", "Curaçao");
      } else if (m1.find()) {
        countryCode = "SX";
        components.put("country", "Sint Maarten");
      } else if (m2.find()) {
        countryCode = "AW";
        components.put("country", "Aruba");
      }
    }

    components.put("country_code", countryCode);
    return components;
  }

  Map<String, Object> cleanupInput(Map<String, Object> components, JsonNode replacements) {
    Object country = components.get("country");
    Object state = components.get("state");

    if (country != null && state != null && Kt.toIntOrNull((String) country) != null) {
      components.put("country", state);
      components.remove("state");
    }
    if (replacements != null && replacements.size() > 0) {
      for (String component : components.keySet()) {
        Iterator<JsonNode> rIterator = replacements.iterator();
        String regex = String.format("^%s=", component);
        Pattern p = regexPatternCache.get(regex);
        while (rIterator.hasNext()) {
          ArrayNode replacement = (ArrayNode) rIterator.next();
          Matcher m = p.matcher(replacement.get(0).asText());
          if (m.find()) {
            m.reset();
            String value = m.replaceAll("");
            if (components.get(component).toString().equals(value)) {
              components.put(component, replacement.get(1).asText());
            }
            m.reset();
          } else {
            Pattern p2 = regexPatternCache.get(replacement.get(0).asText());
            Matcher m2 = p2.matcher(components.get(component).toString());
            String value = m2.replaceAll(replacement.get(1).asText());
            m.reset();
            components.put(component, value);
          }
        }
      }
    }

    if (!components.containsKey("state_code")  && components.containsKey("state")) {
      String stateCode = getStateCode(components.get("state").toString(), components.get("country_code").toString());
      components.put("state_code", stateCode);
      Pattern p = regexPatternCache.get("^washington,? d\\.?c\\.?");
      Matcher m = p.matcher(components.get("state").toString());
      if (m.find()) {
        components.put("state_code", "DC");
        components.put("state", "District of Columbia");
        components.put("city", "Washington");
      }
    }

    if (!components.containsKey("county_code") && components.containsKey("county")) {
      String countyCode = getCountyCode(components.get("county").toString(), components.get("country_code").toString());
      components.put("county_code", countyCode);
    }

    List<String> unknownComponents = components.entrySet().stream().filter(component -> {
      if (component.getKey() == null) {
        return false;
      }
      return !knownComponents.contains(component.getKey());
    }).map(component -> component.getValue().toString()).collect(Collectors.toList());

    if (appendUnknown && unknownComponents.size() > 0) {
      components.put("attention", String.join(", ", unknownComponents));
    }


    if (components.containsKey("postcode")) {
      String postCode = components.get("postcode").toString();
      components.put("postcode", postCode);
      Pattern p1 = regexPatternCache.get("^(\\d{5}),\\d{5}");
      Pattern p2 = regexPatternCache.get("\\d+;\\d+");
      Matcher m1 = p1.matcher(postCode);
      Matcher m2 = p2.matcher(postCode);
      if (postCode.length() > 20) {
        components.remove("postcode");
      } else if (m2.matches()) {
        components.remove("postcode");
      } else if (m1.matches()) {
        components.put("postcode", m1.group(1));
      }
    }

    if (abbreviate && components.containsKey("country_code") && Templates.COUNTRY_2_LANG.getData().has(components.get("country_code").toString())) {
      JsonNode languages = Templates.COUNTRY_2_LANG.getData().get(components.get("country_code").toString());
      StreamSupport.stream(languages.spliterator(), false)
          .filter(language -> Templates.ABBREVIATIONS.getData().has(language.textValue()))
          .map(language -> Templates.ABBREVIATIONS.getData().get(language.textValue())).forEach(
          languageAbbreviations -> StreamSupport.stream(languageAbbreviations.spliterator(), false)
              .filter(abbreviation -> abbreviation.has("component"))
              .forEach(abbreviation -> StreamSupport.stream(abbreviation.get("replacements").spliterator(), false)
                  .forEach(replacement -> {
                    String key = abbreviation.get("component").asText();
                    if (key == null) {
                      return;
                    }
                    if (components.get(key) == null) {
                      return;
                    }
                    String oldComponent = components.get(key).toString();
                    String regex = String.format("\\b%s\\b", replacement.get("src").asText());
                    Pattern p = regexPatternCache.get(regex);
                    Matcher m = p.matcher(oldComponent);
                    String newComponent = m.replaceAll(replacement.get("dest").asText());
                    components.put(key, newComponent);
                  })));
    }

    Pattern p = regexPatternCache.get("^https?://");
    return components.entrySet().stream().filter(component -> {
      if (component.getValue() == null) {
        return false;
      }

      Matcher m = p.matcher(component.getValue().toString());

      if (m.matches()) {
        m.reset();
        return false;
      }

      m.reset();
      return true;
    }).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  Map<String, Object> applyAliases(Map<String, Object> components) {
    Map<String, Object> aliasedComponents = new HashMap<>();
    components.forEach((key, value) -> {
      String newKey = key;
      Iterator<JsonNode> iterator = Templates.ALIASES.getData().elements();
      while (iterator.hasNext()) {
        JsonNode pair = iterator.next();
        if (pair.get("alias").asText().equals(key)
            && components.get(pair.get("name").asText()) == null) {
          newKey = pair.get("name").asText();
          break;
        }
      }
      aliasedComponents.put(key, value);
      aliasedComponents.put(newKey, value);
    });

    return aliasedComponents;
  }

  JsonNode findTemplate(Map<String, Object> components) {
    JsonNode template;
    if (Templates.WORLDWIDE.getData().has(components.get("country_code").toString())) {
      template = Templates.WORLDWIDE.getData().get(components.get("country_code").toString());
    } else {
      template = Templates.WORLDWIDE.getData().get("default");
    }

    return template;
  }

  JsonNode chooseTemplateText(JsonNode template, Map<String, Object> components) {
    JsonNode selected;
    if (template.has("address_template")) {
      if (Templates.WORLDWIDE.getData().has(template.get("address_template").asText())) {
        selected = Templates.WORLDWIDE.getData().get(template.get("address_template").asText());
      } else {
        selected = template.get("address_template");
      }
    } else {
      JsonNode defaults = Templates.WORLDWIDE.getData().get("default");
      selected = Templates.WORLDWIDE.getData().get(defaults.get("address_template").textValue());
    }

    List<String> required = Arrays.asList("road", "postcode");
    long count = required.stream().filter(req -> !components.containsKey(req)).count();
    if (count == 2) {
      if (template.has("fallback_template")) {
        if (Templates.WORLDWIDE.getData().has(template.get("fallback_template").asText())) {
          selected = Templates.WORLDWIDE.getData().get(template.get("fallback_template").asText());
        } else {
          selected = template.get("fallback_template");
        }
      } else {
        JsonNode defaults = Templates.WORLDWIDE.getData().get("default");
        selected = Templates.WORLDWIDE.getData().get(defaults.get("fallback_template").textValue());
      }
    }
    return selected;
  }

  String getStateCode(String state, String countryCode) {
    if (!Templates.STATE_CODES.getData().has(countryCode)) {
      return null;
    }

    JsonNode countryCodes = Templates.STATE_CODES.getData().get(countryCode);
    Iterator<String> iterator = countryCodes.fieldNames();
    return StreamSupport
        .stream(Spliterators.spliteratorUnknownSize(iterator,
        Spliterator.ORDERED), false).filter(key-> {
          JsonNode code = countryCodes.get(key);
      if (code.isObject()) {
        if (code.has("default")) {
          return code.get("default").asText().toUpperCase().equals(state.toUpperCase());
        }
      } else {
        return code.asText().toUpperCase().equals(state.toUpperCase());
      }
      return false;
    }).findFirst().orElse(null);
  }

  String getCountyCode(String county, String countryCode) {
    if (!Templates.COUNTY_CODES.getData().has(countryCode)) {
      return null;
    }
    JsonNode country = Templates.COUNTY_CODES.getData().get(countryCode);
    Optional<JsonNode> countyCode = StreamSupport.stream(country.spliterator(), true).filter(posCounty -> {
      if (posCounty.isObject()) {
        if (posCounty.has("default")) {
          return posCounty.get("default").asText().toUpperCase().equals(county.toUpperCase());
        }
      } else {
        return posCounty.asText().toUpperCase().equals(county.toUpperCase());
      }
      return false;
    }).findFirst();

    return countyCode.map(JsonNode::asText).orElse(null);
  }

  String renderTemplate(JsonNode template, Map<String, Object> components) {
    Map<String, Object> callback = new HashMap<>();
    callback.put("first", (Function<String, String>) s -> {
      String[] splitted = s.split("\\s*\\|\\|\\s*");
      Optional<String> chosen = Arrays.stream(splitted).filter(v -> v.length() > 0).findFirst();
      return chosen.orElse("");
    });

    JsonNode templateText = chooseTemplateText(template, components);
    MustacheFactory mf = new DefaultMustacheFactory();
    Mustache m = mf.compile(new StringReader(templateText.asText()), "example");
    StringWriter st = new StringWriter();
    m.execute(st, new Object[]{ components, callback});
    String rendered = cleanupRender(st.toString());

    if (template.has("postformat_replace")) {
      ArrayNode postformat = (ArrayNode) template.get("postformat_replace");
      for (JsonNode regex : postformat) {
        Pattern p = regexPatternCache.get(regex.get(0).asText());
        Matcher m2 = p.matcher(rendered);
        rendered = m2.replaceAll(regex.get(1).asText());
      }
    }
    rendered = cleanupRender(rendered);
    String trimmed = rendered.trim();

    return trimmed + "\n";
  }

  String cleanupRender(String rendered) {
    Set<Map.Entry<String, String>> entries = replacements.entrySet();
    String deduped = rendered;

    for(Map.Entry<String, String> replacement : entries) {
      Pattern p = regexPatternCache.get(replacement.getKey());
      Matcher m = p.matcher(deduped);
      String predupe = m.replaceAll(replacement.getValue());
      deduped = dedupe(predupe);
    }

    return deduped;
  }

  String dedupe(String rendered) {
     return Arrays.stream(rendered.split("\n"))
        .map(s -> Arrays.stream(s.trim().split(", "))
            .map(String::trim).distinct().collect(Collectors.joining(", ")))
        .distinct()
        .collect(Collectors.joining("\n"));
  }

  static List<String> getKnownComponents() {
    List<String> knownComponents = new ArrayList<>();
    Iterator<JsonNode> fields = Templates.ALIASES.getData().elements();
    while (fields.hasNext()) {
      JsonNode field = fields.next();
      knownComponents.add(field.get("alias").textValue());
    }

    return knownComponents;
  }
}
