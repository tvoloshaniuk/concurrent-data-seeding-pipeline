package ua.shpp;


import org.junit.jupiter.api.Test;
import ua.shpp.db.DbRepository;
import ua.shpp.utils.ResourceLoader;

import static org.mockito.Mockito.*;

public class EpicenterSeedingServiceTest {
    private final DbRepository dbRepository = mock(DbRepository.class);

    /* RecreateSchema */
    @Test
    public void execute_runsDropAllTablesScript_whenRecreateSchemaOn() {
        verify(dbRepository).runDdl(ResourceLoader.readText("drop_all_tables.sql"));
    }

    // execute_runsSchemaCreationScript_whenRecreateSchemaOn

    //todo execute_..._whenRecreateSchemaOn() кейси, що повязані з seed (fillFoundationTables, fillShopEntryTable)

    @Test
    public void execute_skipsDropAllTablesScript_whenRecreateSchemaOff() {
        new EpicenterSeedingService(config(false, false), dbRepository).execute();

        verify(dbRepository, never()).runDdl(ResourceLoader.readText("drop_all_tables.sql"));
    }



    /* RecreateIndexes */

    //execute_runsDropIndexesScript_whenRecreateIndexeOn()

    /* Both (RecreateSchema and RecreateIndexes) */

    @Test
    void execute_runsNoDdl_whenBothModesOff() {
        new EpicenterSeedingService(config(false, false), dbRepository).execute();

        verify(dbRepository, never()).runDdl(anyString());
    }

    /* Other */
    // todo  тест на порядок drop перед create через dbRepository.runDdlcontains("DROP TABLE")... contains("CREATE TABLE")
    //todo verifyItemTypeIsSearchable() { when(dbRepository.existsItemType(anyString())).thenReturn(false); }




















}