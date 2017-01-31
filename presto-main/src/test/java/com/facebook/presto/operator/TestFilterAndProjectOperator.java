/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.operator;

import com.facebook.presto.metadata.Signature;
import com.facebook.presto.operator.project.PageProcessor;
import com.facebook.presto.spi.Page;
import com.facebook.presto.sql.gen.ExpressionCompiler;
import com.facebook.presto.sql.planner.plan.PlanNodeId;
import com.facebook.presto.sql.relational.RowExpression;
import com.facebook.presto.testing.MaterializedResult;
import com.google.common.collect.ImmutableList;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static com.facebook.presto.RowPagesBuilder.rowPagesBuilder;
import static com.facebook.presto.SessionTestUtils.TEST_SESSION;
import static com.facebook.presto.metadata.MetadataManager.createTestMetadataManager;
import static com.facebook.presto.operator.OperatorAssertion.assertOperatorEquals;
import static com.facebook.presto.spi.function.OperatorType.ADD;
import static com.facebook.presto.spi.function.OperatorType.BETWEEN;
import static com.facebook.presto.spi.type.BigintType.BIGINT;
import static com.facebook.presto.spi.type.BooleanType.BOOLEAN;
import static com.facebook.presto.spi.type.VarcharType.VARCHAR;
import static com.facebook.presto.sql.relational.Expressions.call;
import static com.facebook.presto.sql.relational.Expressions.constant;
import static com.facebook.presto.sql.relational.Expressions.field;
import static com.facebook.presto.testing.TestingTaskContext.createTaskContext;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static java.util.concurrent.Executors.newCachedThreadPool;

@Test(singleThreaded = true)
public class TestFilterAndProjectOperator
{
    private ExecutorService executor;
    private DriverContext driverContext;

    @BeforeMethod
    public void setUp()
    {
        executor = newCachedThreadPool(daemonThreadsNamed("test-%s"));

        driverContext = createTaskContext(executor, TEST_SESSION)
                .addPipelineContext(0, true, true)
                .addDriverContext();
    }

    @AfterMethod
    public void tearDown()
    {
        executor.shutdownNow();
    }

    @Test
    public void test()
            throws Exception
    {
        List<Page> input = rowPagesBuilder(VARCHAR, BIGINT)
                .addSequencePage(100, 0, 0)
                .build();

        RowExpression filter = call(
                Signature.internalOperator(BETWEEN, BOOLEAN.getTypeSignature(), ImmutableList.of(BIGINT.getTypeSignature(), BIGINT.getTypeSignature(), BIGINT.getTypeSignature())),
                BOOLEAN,
                field(1, BIGINT),
                constant(10L, BIGINT),
                constant(19L, BIGINT));

        RowExpression field0 = field(0, VARCHAR);
        RowExpression add5 = call(
                Signature.internalOperator(ADD, BIGINT.getTypeSignature(), ImmutableList.of(BIGINT.getTypeSignature(), BIGINT.getTypeSignature())),
                BIGINT,
                field(1, BIGINT),
                constant(5L, BIGINT));

        ExpressionCompiler compiler = new ExpressionCompiler(createTestMetadataManager());
        Supplier<PageProcessor> processor = compiler.compilePageProcessor(Optional.of(filter), ImmutableList.of(field0, add5));

        OperatorFactory operatorFactory = new FilterAndProjectOperator.FilterAndProjectOperatorFactory(
                0,
                new PlanNodeId("test"),
                processor,
                ImmutableList.of(VARCHAR, BIGINT));

        MaterializedResult expected = MaterializedResult.resultBuilder(driverContext.getSession(), VARCHAR, BIGINT)
                .row("10", 15L)
                .row("11", 16L)
                .row("12", 17L)
                .row("13", 18L)
                .row("14", 19L)
                .row("15", 20L)
                .row("16", 21L)
                .row("17", 22L)
                .row("18", 23L)
                .row("19", 24L)
                .build();

        assertOperatorEquals(operatorFactory, driverContext, input, expected);
    }
}
