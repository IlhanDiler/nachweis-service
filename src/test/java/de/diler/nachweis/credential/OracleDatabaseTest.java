package de.diler.nachweis.credential;

import de.diler.nachweis.OracleTestcontainersConfiguration;
import org.springframework.context.annotation.Import;

@Import(OracleTestcontainersConfiguration.class)
class OracleDatabaseTest extends CredentialDatabaseContractTest {
}
