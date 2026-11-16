import com.vitorpamplona.quartz.utils.EventFactory;
import com.vitorpamplona.quartz.kinds.KindNames;
public class KnownKinds {
  public static void main(String[] a) throws Exception {
    var sb = new StringBuilder();
    for (int k = 0; k <= 65535; k++) {
      boolean typed = EventFactory.Companion.isKnownKind(k);
      var info = KindNames.INSTANCE.infoFor(k);
      if (typed || info != null) sb.append(k).append('\t').append(typed ? "typed" : "named-only").append('\t').append(info == null ? "" : info.getName()).append('\n');
    }
    java.nio.file.Files.writeString(java.nio.file.Path.of(a[0]), sb.toString());
  }
}
