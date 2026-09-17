package ua.shpp;


import org.junit.jupiter.api.Test;
import ua.shpp.db.DbRepository;
import ua.shpp.utils.ResourceLoader;

import static org.mockito.Mockito.*;

public class EpicenterSeedingServiceTest {
    private final DbRepository dbRepository = mock(DbRepository.class);

    @Test
    public void execute_runsDropAllTablesScript_whenRecreateSchemaOn() {
        verify(dbRepository).runDdl(ResourceLoader.readText("drop_all_tables.sql"));
    }


    @Test
    public void execute_skipsDropAllTablesScript_whenRecreateSchemaOff() {

        verify(dbRepository, never()).runDdl(ResourceLoader.readText("drop_all_tables.sql"));
    }

    //todo execute_runsIndexesFlow_whenRecreateIndexesOn()


//    todo  тест на порядок drop перед create через dbRepository.runDdlcontains("DROP TABLE")... contains("CREATE TABLE")

    //todo public void seed_...
    //todo public void seed_runsSchemaCreationScript {}






}