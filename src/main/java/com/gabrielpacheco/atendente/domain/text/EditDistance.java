package com.gabrielpacheco.atendente.domain.text;

/**
 * Distância de edição OSA (Levenshtein com transposição de letras vizinhas), usada para tolerar erros de digitação.
 */
public final class EditDistance {

    private EditDistance() {
    }

    /**
     * Devolve a distância entre {@code a} e {@code b}, ou {@code max + 1} assim que ficar claro que ela passa de {@code max}.
     */
    public static int osa(String a, String b, int max) {
        if (Math.abs(a.length() - b.length()) > max) {
            return max + 1;
        }
        int[] twoRowsBack = new int[b.length() + 1];
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            int rowMinimum = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int value = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1), previous[j - 1] + substitution);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    value = Math.min(value, twoRowsBack[j - 2] + 1);
                }
                current[j] = value;
                rowMinimum = Math.min(rowMinimum, value);
            }
            // O mínimo de uma linha nunca diminui nas seguintes: dá para desistir cedo.
            if (rowMinimum > max) {
                return max + 1;
            }
            int[] recycled = twoRowsBack;
            twoRowsBack = previous;
            previous = current;
            current = recycled;
        }
        return Math.min(previous[b.length()], max + 1);
    }

    /** Quantos erros se tolera numa palavra digitada: nenhum até 3 letras, 1 até 6 e 2 a partir de 7. */
    public static int typoTolerance(int length) {
        if (length <= 3) {
            return 0;
        }
        return length <= 6 ? 1 : 2;
    }

    /**
     * A primeira letra precisa coincidir, exceto quando as duas primeiras foram trocadas ("gaendar").
     * Isso impede, por exemplo, que "porte" vire "corte".
     */
    public static boolean startsAlike(String typed, String target) {
        if (typed.isEmpty() || target.isEmpty()) {
            return false;
        }
        if (typed.charAt(0) == target.charAt(0)) {
            return true;
        }
        return typed.length() > 1 && target.length() > 1
                && typed.charAt(0) == target.charAt(1) && typed.charAt(1) == target.charAt(0);
    }

    public static boolean matchesWithTypos(String typed, String target) {
        if (typed.equals(target)) {
            return true;
        }
        int tolerance = typoTolerance(typed.length());
        return tolerance > 0 && startsAlike(typed, target) && osa(typed, target, tolerance) <= tolerance;
    }
}
