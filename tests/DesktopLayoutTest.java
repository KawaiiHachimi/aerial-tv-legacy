package app.aerial.legacy;
import java.util.*;
/** Run with java -ea; exercises organization invariants without Android stub methods. */
public final class DesktopLayoutTest {
  private static int checks;
  private static void check(boolean value, String note) {
    checks++;
    if (!value)
      throw new AssertionError(note);
  }
  private static void invariant(DesktopLayout l, List<String> installed) {
    Set<String> seen = new HashSet<String>(l.hidden);
    for (DesktopLayout.Entry e : l.entries) {
      if (e.folder()) {
        check(!e.members.isEmpty(), "empty folder");
        for (String p : e.members) check(seen.add(p), "duplicate folder app " + p);
      } else
        check(seen.add(e.key), "duplicate root app " + e.key);
    }
    check(seen.equals(new HashSet<String>(installed)), "lost or stale package");
  }
  public static void main(String[] args) {
    DesktopLayout l = new DesktopLayout();
    List<String> installed = new ArrayList<String>();
    for (int i = 0; i < 37; i++) installed.add("app.test.p" + i);
    l.reconcile(installed);
    check(l.entries.size() == 37, "initial install");
    String id = l.folder(installed.get(0), null, "视频 / TV: 电影 🎬");
    l.folder(installed.get(1), id, "");
    check(l.find(id).members.size() == 2, "merge folder");
    invariant(l, installed);
    String encoded = l.encode();
    DesktopLayout restored = new DesktopLayout();
    restored.decode(encoded);
    check(restored.encode().equals(encoded), "Unicode folder round trip");
    l.takeOut(installed.get(0));
    check(l.find(installed.get(0)) != null, "take out");
    l.ungroup(id);
    check(l.find(id) == null && l.find(installed.get(1)) != null, "ungroup");
    l.hide(installed.get(2));
    l.reconcile(installed);
    check(l.find(installed.get(2)) == null, "hidden stays hidden");
    l.show(installed.get(2));
    check(l.find(installed.get(2)) != null, "unhide");
    String before = l.encode();
    l.move(0, 4);
    l.decode(before);
    check(l.encode().equals(before), "move cancel restores snapshot");
    Random random = new Random(18);
    for (int n = 0; n < 2000; n++) {
      String pkg = installed.get(random.nextInt(installed.size()));
      switch (random.nextInt(6)) {
        case 0:
          l.hide(pkg);
          break;
        case 1:
          l.show(pkg);
          break;
        case 2:
          l.takeOut(pkg);
          break;
        case 3:
          l.move(random.nextInt(Math.max(1, l.entries.size())),
              random.nextInt(Math.max(1, l.entries.size())));
          break;
        case 4:
          String f = l.folder(pkg, null, "组 " + n);
          if (random.nextBoolean())
            l.ungroup(f);
          break;
        default:
          l.decode(l.encode());
          break;
      }
      l.reconcile(installed);
      invariant(l, installed);
    }
    installed.remove(0);
    l.reconcile(installed);
    invariant(l, installed);
    check(!l.encode().contains("app.test.p0\n"), "uninstall cleaned up");
    DesktopLayout bad = new DesktopLayout();
    bad.decode("not a saved layout");
    bad.reconcile(installed);
    invariant(bad, installed);
    System.out.println("DesktopLayout: " + checks
        + (" checks passed; 2000 organization mutations preserved every installed app exactly "
           + "once."));
  }
}
