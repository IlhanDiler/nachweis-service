package de.diler.nachweis.credential;

import de.diler.nachweis.TestcontainersConfiguration;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
class PostgresDatabaseTest extends CredentialDatabaseContractTest {
}
