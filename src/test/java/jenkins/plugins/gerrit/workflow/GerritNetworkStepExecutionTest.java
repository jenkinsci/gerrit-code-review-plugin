// Copyright (C) 2026 NVIDIA Corporation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package jenkins.plugins.gerrit.workflow;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import hudson.EnvVars;
import hudson.model.TaskListener;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.Future;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.junit.Test;

public class GerritNetworkStepExecutionTest {

  @Test
  public void commentsRunOutsideTheCpsVmThread() {
    assertTrue(
        SynchronousNonBlockingStepExecution.class.isAssignableFrom(
            GerritCommentStep.Execution.class));
  }

  @Test
  public void reviewRunsOutsideTheCpsVmThread() {
    assertTrue(
        SynchronousNonBlockingStepExecution.class.isAssignableFrom(
            GerritReviewStep.Execution.class));
  }

  @Test
  public void checksRunOutsideTheCpsVmThread() {
    assertTrue(
        SynchronousNonBlockingStepExecution.class.isAssignableFrom(
            GerritCheckStep.Execution.class));
  }

  @Test
  public void executionsDoNotRetainNonSerializableOuterSteps() {
    assertStaticImmutableExecution(GerritCommentStep.Execution.class);
    assertStaticImmutableExecution(GerritReviewStep.Execution.class);
    assertStaticImmutableExecution(GerritCheckStep.Execution.class);
  }

  @Test
  public void interruptionFencePreservesInterruptAndStopsMutation() {
    Thread.currentThread().interrupt();
    try {
      GerritStepInterruption.checkBeforeMutation();
      fail("Expected an interrupted publication to stop before mutation");
    } catch (InterruptedException expected) {
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  public void workflowStepApiPreservesTheOriginalStopCause() throws Exception {
    StepContext context = mock(StepContext.class);
    Future<?> task = mock(Future.class);
    Throwable stopCause = new Exception("Pipeline stopped");
    TestExecution execution = new TestExecution(context);
    Field taskField = SynchronousNonBlockingStepExecution.class.getDeclaredField("task");
    taskField.setAccessible(true);
    taskField.set(execution, task);

    execution.stop(stopCause);

    verify(task).cancel(true);
    verify(context).onFailure(stopCause);
  }

  private static void assertStaticImmutableExecution(Class<?> executionClass) {
    assertTrue(Modifier.isStatic(executionClass.getModifiers()));
    for (Field field : executionClass.getDeclaredFields()) {
      assertFalse(field.getName(), field.isSynthetic() && field.getName().startsWith("this$"));
      if (!Modifier.isStatic(field.getModifiers())) {
        assertTrue(field.getName(), Modifier.isFinal(field.getModifiers()));
        assertFalse(field.getName(), TaskListener.class.isAssignableFrom(field.getType()));
        assertFalse(field.getName(), EnvVars.class.isAssignableFrom(field.getType()));
      }
    }
  }

  private static class TestExecution extends SynchronousNonBlockingStepExecution<Void> {
    private static final long serialVersionUID = 1L;

    TestExecution(StepContext context) {
      super(context);
    }

    @Override
    protected Void run() {
      return null;
    }
  }
}
