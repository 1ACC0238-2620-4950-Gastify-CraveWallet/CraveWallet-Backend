package pe.edu.upc.gastify.cravewallet.launch.infrastructure.persistence;

import jakarta.persistence.*;
import pe.edu.upc.gastify.cravewallet.launch.domain.model.LaunchSubscriber;

import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "launch_subscribers")
public class LaunchSubscriberEntity {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(nullable = false, length = 5) private String locale;
    @Column(name = "subscribed_at", nullable = false) private Instant subscribedAt;

    protected LaunchSubscriberEntity() { }

    public LaunchSubscriberEntity(LaunchSubscriber subscriber) {
        id = subscriber.id(); email = subscriber.email(); locale = subscriber.locale(); subscribedAt = subscriber.subscribedAt();
    }
}
