package com.github.xiaobo9.commons.kit;

import java.io.UnsupportedEncodingException;
import java.util.Map;

public class ParameterKit {

    public static void parseParameters(Map<String, String[]> map, String data, String encoding) throws UnsupportedEncodingException {
        if (data == null || data.length() <= 0) {
            return;
        }

        byte[] bytes;
        if (encoding == null) {
            bytes = data.getBytes();
        } else {
            bytes = data.getBytes(encoding);
        }
        parseParameters(map, bytes, encoding);
    }


    public static void parseParameters(Map<String, String[]> map, byte[] data, String encoding) throws UnsupportedEncodingException {
        if ((data != null) && (data.length > 0)) {
            innerParse(map, data, encoding);
        }
    }

    private static void innerParse(Map<String, String[]> map, byte[] data, String encoding) throws UnsupportedEncodingException {
        int ix = 0;
        int ox = 0;
        String key = null;
        String value = null;
        while (ix < data.length) {
            byte c = data[(ix++)];
            switch ((char) c) {
                case '&':
                    value = new String(data, 0, ox, encoding);
                    if (key != null) {
                        putMapEntry(map, key, value);
                        key = null;
                    }
                    ox = 0;
                    break;
                case '=':
                    if (key == null) {
                        key = new String(data, 0, ox, encoding);
                        ox = 0;
                    } else {
                        data[(ox++)] = c;
                    }
                    break;
                case '+':
                    data[(ox++)] = 32;
                    break;
                case '%':
                    data[(ox++)] = (byte) ((convertHexDigit(
                            data[(ix++)]) << 4) + convertHexDigit(data[(ix++)]));

                    break;
                default:
                    data[(ox++)] = c;
            }
        }

        if (key != null) {
            value = new String(data, 0, ox, encoding);
            putMapEntry(map, key, value);
        }
    }

    public static void putMapEntry(Map<String, String[]> map, String name, String value) {
        String[] newValues;
        String[] oldValues = map.get(name);
        if (oldValues == null) {
            newValues = new String[1];
            newValues[0] = value;
        } else {
            newValues = new String[oldValues.length + 1];
            System.arraycopy(oldValues, 0, newValues, 0, oldValues.length);
            newValues[oldValues.length] = value;
        }
        map.put(name, newValues);
    }

    public static byte convertHexDigit(byte b) {
        if ((b >= 48) && (b <= 57)) {
            return (byte) (b - 48);
        }
        if ((b >= 97) && (b <= 102)) {
            return (byte) (b - 97 + 10);
        }
        if ((b >= 65) && (b <= 70)) {
            return (byte) (b - 65 + 10);
        }
        return 0;
    }

}
