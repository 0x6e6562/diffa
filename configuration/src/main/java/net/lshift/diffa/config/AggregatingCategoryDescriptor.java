/**
 * Copyright (C) 2010-2011 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lshift.diffa.config;

import org.codehaus.jackson.annotate.JsonSubTypes;

/**
 * This provides various endpoint-specific attributes of a category that are necessary for the kernel
 * to be able auto-narrow a category.
 */
@JsonSubTypes({
  @JsonSubTypes.Type(value = RangeCategoryDescriptor.class, name = "range"),
  @JsonSubTypes.Type(value = SetCategoryDescriptor.class, name = "set"),
  @JsonSubTypes.Type(value = PrefixCategoryDescriptor.class, name = "prefix")
})
abstract public class AggregatingCategoryDescriptor extends CategoryDescriptor {
  public void ensureOrderedOrNotAggregated(String path, String collationOrder) {
    if (collationOrder.equals("unordered")) { // UnorderedCollationOrdering.name() == "unordered"
      throw new InvalidAggregationConfigurationException(path);
    }
  }
}
