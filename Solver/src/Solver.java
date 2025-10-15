
/**
 *
 * @author estelle phalippou
 *
 *
 */
public class Solver {

    private long target;  // the SSP target
    private long[] integers;  // the integers s_i
    private boolean[] solution;  // the x variables (one boolean per variable)
    private double[][] matrix;  // the QUBO matrix Q
    private volatile long error;  // attribute used to store the current error (volatile for visibility)

    private final java.util.Random random = new java.util.Random();
    private final Object solutionLock = new Object();

    public Solver(int n) {

        integers = new long[n];
        matrix = new double[n][n];
        solution = new boolean[n];

        for (int i = 0; i < n; i++) {
            integers[i] = 1 + random.nextInt(100);
        }

        target = 0L;
        for (int i = 0; i < n; i++) {
            if (random.nextBoolean()) {
                target += integers[i];
                solution[i] = true;
            } else {
                solution[i] = false;
            }
        }

        for (int i = 0; i < n; i++) {
            solution[i] = false;
        }

        generateQUBOMatrix();

        normalizeMatrix();

        computeError();
    }

    private void generateQUBOMatrix() {
        int n = integers.length;
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                if (i == j) {
                    matrix[i][i] = (double) (integers[i] * integers[i] - 2L * target * integers[i]);
                } else {
                    matrix[i][j] = 2.0 * integers[i] * integers[j];
                    matrix[j][i] = matrix[i][j];
                }
            }
        }
    }

    private void normalizeMatrix() {
        int n = matrix.length;
        double maxAbs = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                maxAbs = Math.max(maxAbs, Math.abs(matrix[i][j]));
            }
        }
        if (maxAbs > 0.0) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    matrix[i][j] /= maxAbs;
                }
            }
        }
    }

    private synchronized void computeError() {
        long sum = 0L;
        for (int i = 0; i < solution.length; i++) {
            if (solution[i]) {
                sum += integers[i];
            }
        }
        this.error = target - sum;
    }

    private class VerifAgent extends Thread {

        @Override
        public void run() {
            while (Solver.this.error != 0L) {
                Solver.this.computeError();
                try {
                    Thread.sleep(2);
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }
    }

    private class SpinAgent extends Thread {

        private final int i;
        private final double bias;

        SpinAgent(int i, double bias) {
            this.i = i;
            this.bias = bias;
        }

        @Override
        public void run() {
            boolean encouraged = bias < 0.0;
            while (Solver.this.error != 0L && !isInterrupted()) {
                synchronized (solutionLock) {
                    if (solution[i] != encouraged) {
                        if (random.nextDouble() < Math.min(1.0, Math.abs(bias))) {
                            solution[i] = encouraged;
                        }
                    }
                }
                try {
                    Thread.sleep(2);
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }
    }

    private class CouplingAgent extends Thread {

        private final int i, j;
        private final double coupling;

        CouplingAgent(int i, int j, double coupling) {
            this.i = i;
            this.j = j;
            this.coupling = coupling;
        }

        @Override
        public void run() {
            while (Solver.this.error != 0L && !isInterrupted()) {
                synchronized (solutionLock) {
                    if (solution[i] && solution[j]) {
                        if (random.nextDouble() < Math.min(1.0, Math.abs(coupling))) {
                            if (random.nextBoolean()) {
                                solution[i] = false;
                            } else {
                                solution[j] = false;
                            }
                        }
                    }
                }
                try {
                    Thread.sleep(2);
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }
    }

    public void solve() throws InterruptedException {
        int n = integers.length;
        System.out.println("Starting solver with n=" + n + " target=" + target);

        VerifAgent verif = new VerifAgent();
        verif.start();

        SpinAgent[] spins = new SpinAgent[n];
        for (int i = 0; i < n; i++) {
            spins[i] = new SpinAgent(i, matrix[i][i]);
            spins[i].start();
        }

        java.util.List<CouplingAgent> couplings = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                CouplingAgent ca = new CouplingAgent(i, j, matrix[i][j]);
                couplings.add(ca);
                ca.start();
            }
        }

        verif.join();

        for (SpinAgent s : spins) {
            s.interrupt();
        }
        for (CouplingAgent c : couplings) {
            c.interrupt();
        }

        for (SpinAgent s : spins) {
            s.join();
        }
        for (CouplingAgent c : couplings) {
            c.join();
        }

        System.out.println("Solution found (or search stopped):");
        printSolution();
    }

    private void printSolution() {
        System.out.println("Error: " + error + " Target: " + target);
        long sum = 0L;
        System.out.print("Selected integers: ");
        for (int i = 0; i < integers.length; i++) {
            if (solution[i]) {
                System.out.print(integers[i] + " ");
                sum += integers[i];
            }
        }
        System.out.println();
        System.out.println("Sum = " + sum + " (target " + target + ")");
    }

    public static void main(String[] args) throws InterruptedException {
        Solver solver = new Solver(12);
        solver.solve();
    }
}
