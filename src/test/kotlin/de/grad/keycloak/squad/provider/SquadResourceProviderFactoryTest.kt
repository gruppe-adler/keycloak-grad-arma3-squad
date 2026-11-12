package de.grad.keycloak.squad.provider

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.keycloak.services.resource.RealmResourceProviderFactory
import java.util.ServiceLoader

class SquadResourceProviderFactoryTest {

    @Test
    fun `factory is discoverable via ServiceLoader`() {
        val factories = ServiceLoader.load(RealmResourceProviderFactory::class.java)
            .filterIsInstance<SquadResourceProviderFactory>()

        assertEquals(1, factories.size)
        assertEquals("arma3-squad", factories.single().id)
    }
}
