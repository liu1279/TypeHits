public class Until {
    public static String getLevelBlanks(int level) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < 2 * level; i++) {
            stringBuilder.append(" ");
        }
        return stringBuilder.toString();
    }
}
