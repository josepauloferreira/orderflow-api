package io.github.josepauloferreira.orderflow.catalog.api;

import io.github.josepauloferreira.orderflow.catalog.application.InvalidProductPreconditionException;
import io.github.josepauloferreira.orderflow.catalog.application.PreconditionRequiredException;
import java.util.regex.Pattern;

/** Strong HTTP entity tags for the product aggregate's optimistic-lock version. */
public final class ProductEtag {

  private static final Pattern STRONG_VERSION = Pattern.compile("^\"(0|[1-9][0-9]*)\"$");

  private ProductEtag() {}

  public static String of(long version) {
    return "\"" + version + "\"";
  }

  public static long require(String ifMatch) {
    if (ifMatch == null) {
      throw new PreconditionRequiredException();
    }
    var matcher = STRONG_VERSION.matcher(ifMatch);
    if (!matcher.matches()) {
      throw new InvalidProductPreconditionException();
    }
    try {
      return Long.parseLong(matcher.group(1));
    } catch (NumberFormatException e) {
      throw new InvalidProductPreconditionException();
    }
  }
}
