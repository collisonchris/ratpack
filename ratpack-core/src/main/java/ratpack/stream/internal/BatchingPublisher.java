package ratpack.stream.internal;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import ratpack.func.Action;

public class BatchingPublisher<T> extends BufferingPublisher<T> {

  private enum State {Fetching, Writing, Closed, Idle}

  public BatchingPublisher(Publisher<T> upstream, int batchSize, Action<? super T> disposer) {
    super(disposer, write -> {
      return new Subscription() {

        private Subscription subscription;
        private int batchCounter = batchSize;
        private State state = State.Idle;

        @Override
        public void request(long n) {
          if (state == State.Closed) {
            return;
          }

          if (subscription == null) {
            upstream.subscribe(new Subscriber<T>() {

              @Override
              public void onSubscribe(Subscription s) {
                subscription = s;
                s.request(batchSize);
              }

              @Override
              public void onNext(T t) {
                if (state != State.Closed) {
                  state = State.Writing;
                  write.item(t);
                  if (--batchCounter == 0 && write.getRequested() > 0) {
                    maybeFetch();
                  }
                }
              }

              @Override
              public void onError(Throwable t) {
                state = State.Closed;
                write.error(t);
              }

              @Override
              public void onComplete() {
                state = State.Closed;
                write.complete();
              }
            });
          } else {
            if (batchCounter == 0) {
              maybeFetch();
            }
          }
        }

        private void maybeFetch() {
          if (state != State.Fetching) {
            state = State.Fetching;
            batchCounter = batchSize;
            subscription.request(batchSize);
          }
        }

        @Override
        public void cancel() {
          state = State.Closed;
          if (subscription != null) {
            subscription.cancel();
          }
        }
      };
    });
  }

}
