package lk.scot.ums.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.cloud.firestore.Firestore;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FirebaseConfiguration {
    @Bean
    FirebaseApp firebaseApp(
            @Value("${ums.firebase.project-id}") String projectId
    ) throws IOException {
        return FirebaseApp.initializeApp(
                FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .setProjectId(projectId)
                        .build()
        );
    }

    @Bean
    Firestore firestore(
            FirebaseApp firebaseApp,
            @Value("${ums.firebase.database-id}") String databaseId
    ) {
        return FirestoreClient.getFirestore(firebaseApp, databaseId);
    }
}
