/*
 * Copyright 2013 The Closure Compiler Authors.
 *
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

package com.google.javascript.jscomp.newtypes;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A builder for {@link FunctionType} and {@link DeclaredFunctionType}.
 *
 * The builder is called during both JSDoc parsing and type inference, and
 * these parts use different warning systems, so expect the context to handle
 * the exception appropriately.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public final class FunctionTypeBuilder {
  static class WrongParameterOrderException extends RuntimeException {
    WrongParameterOrderException(String message) {
      super(message);
    }
  }

  private final List<JSType> requiredFormals = new ArrayList<>();
  private final List<JSType> optionalFormals = new ArrayList<>();
  private final Map<String, JSType> outerVars = new LinkedHashMap<>();
  private JSType restFormals = null;
  private JSType returnType = null;
  private boolean loose = false;
  private boolean isAbstract = false;
  private JSType nominalType;
  // Only used to build DeclaredFunctionType for prototype methods
  private JSType receiverType;
  private TypeParameters typeParameters = TypeParameters.EMPTY;

  private final JSTypes commonTypes;

  public FunctionTypeBuilder(JSTypes commonTypes) {
    this.commonTypes = checkNotNull(commonTypes);
  }

  /**
   * Used when the order of required/optional/rest formals in a function jsdoc is wrong.
   */
  public FunctionTypeBuilder addPlaceholderFormal() {
    if (restFormals != null) {
      // Nothing to do here, since there is no way to add a placeholder.
    } else if (!optionalFormals.isEmpty()) {
      optionalFormals.add(this.commonTypes.UNKNOWN);
    } else {
      requiredFormals.add(this.commonTypes.UNKNOWN);
    }
    return this;
  }

  public FunctionTypeBuilder addReqFormal(JSType t) {
    if (!optionalFormals.isEmpty() || restFormals != null) {
      throw new WrongParameterOrderException(
          "Cannot add required formal after optional or rest args");
    }
    requiredFormals.add(t);
    return this;
  }

  public FunctionTypeBuilder addReqFormalToFront(JSType t) {
    if (!optionalFormals.isEmpty() || restFormals != null) {
      throw new WrongParameterOrderException(
          "Cannot add required formal after optional or rest args");
    }
    requiredFormals.add(0, t);
    return this;
  }

  public FunctionTypeBuilder addOptFormal(JSType t) {
    if (restFormals != null) {
      throw new WrongParameterOrderException(
          "Cannot add optional formal after rest args");
    }
    if (t == null) {
      optionalFormals.add(null);
    } else {
      checkArgument(!t.isBottom());
      optionalFormals.add(JSType.join(t, this.commonTypes.UNDEFINED));
    }
    return this;
  }

  public FunctionTypeBuilder addOuterVarPrecondition(String name, JSType t) {
    outerVars.put(name, t);
    return this;
  }

  public FunctionTypeBuilder addRestFormals(JSType t) {
    checkState(restFormals == null);
    restFormals = t;
    return this;
  }

  public FunctionTypeBuilder addRetType(JSType t) {
    checkState(returnType == null);
    returnType = t;
    return this;
  }

  public FunctionTypeBuilder addLoose() {
    loose = true;
    return this;
  }

  public FunctionTypeBuilder addAbstract(boolean isAbstract) {
    this.isAbstract = isAbstract;
    return this;
  }

  public FunctionTypeBuilder addNominalType(JSType t) {
    checkState(this.nominalType == null);
    this.nominalType = t;
    return this;
  }

  public FunctionTypeBuilder addTypeParameters(TypeParameters typeParameters) {
    checkNotNull(typeParameters);
    checkState(this.typeParameters.isEmpty());
    this.typeParameters = typeParameters;
    return this;
  }

  /**
   * This method is used to handle functions with .call. For simplicity, if a TTL function
   * is used with .call, the TTL variables will be treated as unknown.
   */
  public FunctionTypeBuilder appendTypeParameters(TypeParameters typeParameters) {
    checkNotNull(typeParameters);
    ArrayList<String> newTypeParams = new ArrayList<>();
    newTypeParams.addAll(this.typeParameters.asList());
    newTypeParams.addAll(typeParameters.asList());
    this.typeParameters = TypeParameters.make(newTypeParams);
    return this;
  }

  public FunctionTypeBuilder addReceiverType(JSType t) {
    // this.receiverType is not always null here, because of prototype methods
    // with an explicit @this annotation
    this.receiverType = t;
    return this;
  }

  public DeclaredFunctionType buildDeclaration() {
    checkState(!loose);
    checkState(outerVars.isEmpty());
    return DeclaredFunctionType.make(
        this.commonTypes,
        requiredFormals, optionalFormals, restFormals, returnType,
        nominalType, receiverType, typeParameters, isAbstract);
  }

  public FunctionType buildFunction() {
    // qmarkFunctionBuilder().buildDeclaration creates a non-loose function,
    // we change that here to have a unique representation in FunctionType.
    if (this.requiredFormals.isEmpty()
        && this.optionalFormals.isEmpty()
        && this.restFormals != null && this.restFormals.isUnknown()
        && this.returnType != null && this.returnType.isUnknown()
        && this.nominalType == null
        && this.receiverType == null
        && this.typeParameters.isEmpty()
        && this.outerVars.isEmpty()) {
      return this.commonTypes.QMARK_FUNCTION;
    }
    return FunctionType.normalized(
        this.commonTypes,
        requiredFormals, optionalFormals, restFormals, returnType,
        nominalType, receiverType, outerVars, typeParameters, loose, isAbstract);
  }
}
