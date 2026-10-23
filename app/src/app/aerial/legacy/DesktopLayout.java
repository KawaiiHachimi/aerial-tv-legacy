package app.aerial.legacy;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Persistent layout, independent of Android UI so mutations can be checked on the host JVM. */
final class DesktopLayout {
  static final class Entry {
    final String key;
    String name;
    final ArrayList<String> members = new ArrayList<String>();
    Entry(String key) {
      this.key = key;
    }
    boolean folder() {
      return key.startsWith("folder-");
    }
  }
  final ArrayList<Entry> entries = new ArrayList<Entry>();
  final LinkedHashSet<String> hidden = new LinkedHashSet<String>();

  Entry find(String key) {
    for (Entry e : entries)
      if (e.key.equals(key))
        return e;
    return null;
  }
  void reconcile(List<String> installed) {
    Set<String> live = new HashSet<String>(installed);
    hidden.retainAll(live);
    Set<String> seen = new HashSet<String>(hidden);
    for (int i = 0; i < entries.size();) {
      Entry e = entries.get(i);
      if (e.folder()) {
        for (int j = 0; j < e.members.size();) {
          String p = e.members.get(j);
          if (!live.contains(p) || !seen.add(p))
            e.members.remove(j);
          else
            j++;
        }
        if (e.members.isEmpty()) {
          entries.remove(i);
          continue;
        }
      } else if (!live.contains(e.key) || !seen.add(e.key)) {
        entries.remove(i);
        continue;
      }
      i++;
    }
    for (String p : installed)
      if (seen.add(p))
        entries.add(new Entry(p));
  }
  void remove(String key) {
    for (int i = entries.size() - 1; i >= 0; i--) {
      Entry e = entries.get(i);
      if (e.key.equals(key))
        entries.remove(i);
      else if (e.folder()) {
        e.members.remove(key);
        if (e.members.isEmpty())
          entries.remove(i);
      }
    }
  }
  void hide(String key) {
    Entry e = find(key);
    if (e != null && e.folder())
      hidden.addAll(e.members);
    else
      hidden.add(key);
    remove(key);
  }
  void show(String key) {
    if (hidden.remove(key))
      entries.add(new Entry(key));
  }
  void move(int from, int to) {
    if (from < 0 || to < 0 || from >= entries.size() || to >= entries.size())
      return;
    Entry e = entries.remove(from);
    entries.add(to, e);
  }
  String folder(String app, String target, String name) {
    Entry e = target == null ? null : find(target);
    if (e != null && !e.folder())
      return null;
    int at = 0;
    for (int i = 0; i < entries.size(); i++)
      if (entries.get(i).key.equals(app))
        at = i;
    remove(app);
    hidden.remove(app);
    if (e == null) {
      e = new Entry("folder-" + java.util.UUID.randomUUID().toString());
      e.name = name;
      entries.add(Math.min(at, entries.size()), e);
    } else if (find(e.key) == null)
      entries.add(e);
    if (!e.members.contains(app))
      e.members.add(app);
    return e.key;
  }
  void takeOut(String app) {
    remove(app);
    hidden.remove(app);
    entries.add(new Entry(app));
  }
  void ungroup(String key) {
    Entry e = find(key);
    if (e == null || !e.folder())
      return;
    int at = entries.indexOf(e);
    entries.remove(e);
    for (String p : e.members) entries.add(at++, new Entry(p));
  }
  String encode() {
    StringBuilder b = new StringBuilder("v1\n");
    for (Entry e : entries) {
      if (!e.folder())
        b.append("A:").append(e.key).append('\n');
      else {
        b.append("F:")
            .append(e.key)
            .append(':')
            .append(escape(e.name == null ? "" : e.name))
            .append(':');
        for (int i = 0; i < e.members.size(); i++) {
          if (i > 0)
            b.append(',');
          b.append(e.members.get(i));
        }
        b.append('\n');
      }
    }
    for (String p : hidden) b.append("H:").append(p).append('\n');
    return b.toString();
  }
  void decode(String text) {
    entries.clear();
    hidden.clear();
    if (text == null || !text.startsWith("v1\n"))
      return;
    for (String line : text.split("\n")) {
      if (line.startsWith("A:"))
        entries.add(new Entry(line.substring(2)));
      else if (line.startsWith("H:"))
        hidden.add(line.substring(2));
      else if (line.startsWith("F:")) {
        String[] parts = line.split(":", 4);
        if (parts.length != 4 || !parts[1].startsWith("folder-"))
          continue;
        Entry e = new Entry(parts[1]);
        e.name = unescape(parts[2]);
        for (String p : parts[3].split(","))
          if (!p.isEmpty())
            e.members.add(p);
        entries.add(e);
      }
    }
  }
  private static String escape(String s) {
    try {
      return URLEncoder.encode(s, "UTF-8");
    } catch (Exception e) {
      return "";
    }
  }
  private static String unescape(String s) {
    try {
      return URLDecoder.decode(s, "UTF-8");
    } catch (Exception e) {
      return s;
    }
  }
}
