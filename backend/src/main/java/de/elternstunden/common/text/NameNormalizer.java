package de.elternstunden.common.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * {@code normalizeName} from Lastenheft 7.1.3: deterministic and independent of the default locale. Used for the
 * family assignment (exact match of first name, last name and birth date) and for the admin search.
 */
public final class NameNormalizer {

    private static final Pattern DASHES_AND_APOSTROPHES = Pattern.compile("[-‐–'’`]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private NameNormalizer() {}

    public static String normalize(String name) {
        String value = Normalizer.normalize(name, Normalizer.Form.NFKC);
        value = DASHES_AND_APOSTROPHES.matcher(value).replaceAll(" ");
        value = WHITESPACE.matcher(value).replaceAll(" ").strip();
        value = value.toLowerCase(Locale.ROOT)
                .replace("ß", "ss")
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue");
        value = Normalizer.normalize(value, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(value).replaceAll("");
    }
}
