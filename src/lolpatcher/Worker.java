package lolpatcher;

/**
 *
 * @author Rick
 */
public abstract class Worker extends Thread{
    public float progress = 1;
    public long startTime = -1;
    String current;
    boolean alternative; // for example true when hashing, false when downloading
}
