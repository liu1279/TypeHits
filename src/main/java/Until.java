public class Until {
    public static String getLevelBlanks(int level) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < 2 * level; i++) {
            stringBuilder.append(" ");
        }
        return stringBuilder.toString();
    }

    public static String getInsertedString(String oldString, String insertBehindString, String needInsertString, int[] bufferIndex) {
        if (bufferIndex[0] > oldString.length() - 1) {
            return null;
        }

        int insertIndex = oldString.indexOf(insertBehindString, bufferIndex[0]);
        if (insertIndex == -1) {
            return null;
        }
        insertIndex += insertBehindString.length();
        bufferIndex[0] = insertIndex + needInsertString.length();
        return oldString.substring(0, insertIndex) + needInsertString + oldString.substring(insertIndex);
    }
}
