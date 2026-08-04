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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.junit.Test;

public class GerritNetworkStepExecutionTest {

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
    assertStaticImmutableExecution(GerritReviewStep.Execution.class);
    assertStaticImmutableExecution(GerritCheckStep.Execution.class);
  }

  private static void assertStaticImmutableExecution(Class<?> executionClass) {
    assertTrue(Modifier.isStatic(executionClass.getModifiers()));
    for (Field field : executionClass.getDeclaredFields()) {
      assertFalse(field.getName(), field.isSynthetic() && field.getName().startsWith("this$"));
      if (!Modifier.isStatic(field.getModifiers())) {
        assertTrue(field.getName(), Modifier.isFinal(field.getModifiers()));
      }
    }
  }
}
