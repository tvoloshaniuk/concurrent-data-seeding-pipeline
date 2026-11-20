package ua.shpp.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.shpp.dto.ShopEntryDto;
import ua.shpp.generation.ShopEntryGenerator;
import ua.shpp.utils.CatalogDimensions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ShopEntryProducerSubtaskTest {
    BlockingQueue<List<ShopEntryDto>> queueMock = new ArrayBlockingQueue<>(10);

    @Test
    public void call_requestsEntriesForItsAssignedShop() throws Exception {
        ShopEntryGenerator generatorMock = mock(ShopEntryGenerator.class);
        int shopId = 2;
        CatalogDimensions dimensions = new CatalogDimensions(2, 1);
        //act
        new ShopEntryProducerSubtask(generatorMock, queueMock, shopId, 1, dimensions)
                .call();
        //assert
        verify(generatorMock).generateForShop(eq(shopId), anyInt(), anyInt());
    }

    private static Stream<Arguments> batchSizesArgumentsProvider() {
        return Stream.of(
                Arguments.of(10, 4, List.of(4, 4, 2)),
                Arguments.of(8, 4, List.of(4, 4)),
                Arguments.of(3, 4, List.of(3)),
                Arguments.of(3, 1, List.of(1, 1, 1))
        );
    }
    @ParameterizedTest
    @MethodSource("batchSizesArgumentsProvider")
    public void call_splitsRecordsIntoExpectedBatchSizes(
            int nElements, int batchSize, List<Integer> expectedBatchSizes
    ) throws Exception {
        BlockingQueue<List<ShopEntryDto>> queue = new ArrayBlockingQueue<>(expectedBatchSizes.size() + 1);
        int shopId = 2;
        List<ShopEntryDto> generatedEntries = new ArrayList<>();
        for (int itemId = 1; itemId <= nElements; itemId++) {
            generatedEntries.add(new ShopEntryDto(itemId, shopId, 1));
        }
        ShopEntryGenerator generatorMock = mock(ShopEntryGenerator.class);
        when(generatorMock.generateForShop(anyInt(), anyInt(), anyInt()))
                .thenReturn(generatedEntries);
        CatalogDimensions dimensions = new CatalogDimensions(2, nElements);
        //act
        new ShopEntryProducerSubtask(generatorMock, queue, shopId, batchSize, dimensions)
                .call();
        //assert
        assertEquals(
                expectedBatchSizes,
                queue.stream().map(List::size).toList()
        );
    }

    @Test
    public void call_queuesEveryGeneratedRecordExactlyOnceWithoutChangingIt() throws Exception {
        BlockingQueue<List<ShopEntryDto>> queue = new ArrayBlockingQueue<>(10);
        int shopId = 2;
        List<ShopEntryDto> generatedEntries = List.of(
                new ShopEntryDto(1, shopId, 1),
                new ShopEntryDto(1, shopId, 1),
                new ShopEntryDto(2, shopId, 1),
                new ShopEntryDto(3, shopId, 1),
                new ShopEntryDto(4, shopId, 1),
                new ShopEntryDto(5, shopId, 1),
                new ShopEntryDto(0, shopId, 1), //invalid
                new ShopEntryDto(-1, shopId, 1) //invalid
                );
        int batchSize = 2;
        ShopEntryGenerator generatorMock = mock(ShopEntryGenerator.class);
        when(generatorMock.generateForShop(anyInt(), anyInt(), anyInt()))
                .thenReturn(generatedEntries);
        CatalogDimensions dimensions = new CatalogDimensions(2, 5);
        //act
        new ShopEntryProducerSubtask(generatorMock, queue, shopId, batchSize, dimensions)
                .call();
        //assert
        List<ShopEntryDto> queuedEntries = queue.stream().flatMap(List::stream).toList();
        assertEquals(generatedEntries, queuedEntries);
        verify(generatorMock).generateForShop(shopId, dimensions.shopsSize(), dimensions.itemCatalogSize());
    }

    @Test
    public void call_returnsQueuedRecordCount() throws Exception {
        BlockingQueue<List<ShopEntryDto>> queue = new ArrayBlockingQueue<>(10);
        int shopId = 2;
        List<ShopEntryDto> generatedEntries = List.of(
                new ShopEntryDto(1, shopId, 1),
                new ShopEntryDto(2, shopId, 1),
                new ShopEntryDto(3, shopId, 1),
                new ShopEntryDto(4, shopId, 1),
                new ShopEntryDto(5, shopId, 1),
                new ShopEntryDto(0, shopId, 1), //invalid
                new ShopEntryDto(-1, shopId, 1) //invalid
        );
        int batchSize = 2;
        ShopEntryGenerator generatorMock = mock(ShopEntryGenerator.class);
        when(generatorMock.generateForShop(anyInt(), anyInt(), anyInt()))
                .thenReturn(generatedEntries);
        CatalogDimensions dimensions = new CatalogDimensions(2, 5);
        //act
        int queuedCount = new ShopEntryProducerSubtask(generatorMock, queue, shopId, batchSize, dimensions)
                .call();
        //assert
        assertEquals(generatedEntries.size(), queuedCount);
    }

    @Test
    public void call_propagatesGeneratorException() {
        BlockingQueue<List<ShopEntryDto>> queue = new ArrayBlockingQueue<>(10);
        int shopId = 2;
        ShopEntryGenerator generatorMock = mock(ShopEntryGenerator.class);
        RuntimeException expected = new RuntimeException("Generator failed");
        when(generatorMock.generateForShop(anyInt(), anyInt(), anyInt()))
                .thenThrow(expected);
        CatalogDimensions dimensions = new CatalogDimensions(2, 5);
        //act & assert
        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> new ShopEntryProducerSubtask(
                        generatorMock, queue, shopId, 2, dimensions
                ).call()
        );
        assertSame(expected, actual);
    }

    @Test
    public void call_propagatesInterruptionWhileWaitingToQueueBatch() throws InterruptedException {
        BlockingQueue<List<ShopEntryDto>> queue = mock(BlockingQueue.class);
        int shopId = 2;
        List<ShopEntryDto> generatedEntries = List.of(
                new ShopEntryDto(1, shopId, 1),
                new ShopEntryDto(2, shopId, 1),
                new ShopEntryDto(3, shopId, 1)
        );
        int batchSize = 2;
        ShopEntryGenerator generatorMock = mock(ShopEntryGenerator.class);
        when(generatorMock.generateForShop(anyInt(), anyInt(), anyInt()))
                .thenReturn(generatedEntries);
        CatalogDimensions dimensions = new CatalogDimensions(2, 3);
        InterruptedException expected = new InterruptedException("Interrupted while queuing batch");
        doThrow(expected).when(queue).put(anyList());
        //act & assert
        InterruptedException actual = assertThrows(
                InterruptedException.class,
                () -> new ShopEntryProducerSubtask(generatorMock, queue, shopId, batchSize, dimensions)
                        .call()
        );
        assertSame(expected, actual);
    }

}
