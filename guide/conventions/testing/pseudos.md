# Java: pseudos

```java
public interface HttpGateway {
  Response fetch(Request request);
}

public final class PseudoHttpGateway implements HttpGateway {

  private final List<Request> received = new ArrayList<>();
  private final Map<URI, Response> canned;

  public PseudoHttpGateway(Map<URI, Response> canned) {
    this.canned = Map.copyOf(canned);
  }

  @Override
  public Response fetch(Request request) {
    received.add(request);
    return Optional.ofNullable(canned.get(request.uri()))
        .orElseThrow(() -> new IllegalStateException("no canned response for " + request.uri()));
  }

  /** What the code under test actually asked for, in order. */
  public List<Request> received() {
    return List.copyOf(received);
  }
}
```
