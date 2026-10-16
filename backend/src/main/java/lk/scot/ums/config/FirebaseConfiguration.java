package lk.scot.ums.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import com.google.cloud.firestore.Firestore;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FirebaseConfiguration {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FirebaseConfiguration.class);

    @Bean
    FirebaseApp firebaseApp(
            @Value("${ums.firebase.project-id}") String projectId
    ) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        GoogleCredentials credentials = resolveCredentials();
        return FirebaseApp.initializeApp(
                FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .setProjectId(projectId)
                        .build()
        );
    }

    private GoogleCredentials resolveCredentials() throws IOException {
        String envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (envPath != null && !envPath.isBlank()) {
            java.nio.file.Path path = java.nio.file.Path.of(envPath);
            if (java.nio.file.Files.exists(path)) {
                log.info("Loading Firebase credentials from GOOGLE_APPLICATION_CREDENTIALS: {}", path.toAbsolutePath());
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(path)) {
                    return GoogleCredentials.fromStream(in);
                }
            }
        }

        java.nio.file.Path[] candidatePaths = {
            java.nio.file.Path.of("ums-privatekey.json"),
            java.nio.file.Path.of("..", "ums-privatekey.json"),
            java.nio.file.Path.of("backend", "ums-privatekey.json")
        };

        for (java.nio.file.Path candidate : candidatePaths) {
            if (java.nio.file.Files.exists(candidate)) {
                log.info("Loading Firebase credentials from local file: {}", candidate.toAbsolutePath().normalize());
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(candidate)) {
                    return GoogleCredentials.fromStream(in);
                }
            }
        }

        return GoogleCredentials.getApplicationDefault();
    }

    @Bean
    Firestore firestore(
            FirebaseApp firebaseApp,
            @Value("${ums.firebase.database-id}") String databaseId
    ) {
        return FirestoreClient.getFirestore(firebaseApp, databaseId);
    }

    @Bean
    FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }
}
