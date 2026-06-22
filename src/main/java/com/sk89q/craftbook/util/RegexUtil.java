package com.sk89q.craftbook.util;

import java.util.regex.Pattern;

public class RegexUtil {

    public static final Pattern EQUALS_PATTERN = Pattern.compile("=", Pattern.LITERAL);
    public static final Pattern COLON_PATTERN = Pattern.compile(":", Pattern.LITERAL);
    public static final Pattern COMMA_PATTERN = Pattern.compile(",", Pattern.LITERAL);
    public static final Pattern IC_PATTERN = Pattern.compile("^\\[(([A-Z]{1,3})[0-9]{1,4})\\][A-Z]?$", Pattern.CASE_INSENSITIVE);

}