package me.modmuss50.fusion.transformer;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import javassist.*;
import javassist.bytecode.Descriptor;
import me.modmuss50.fusion.MixinManager;
import me.modmuss50.fusion.api.Inject;
import me.modmuss50.fusion.api.Rewrite;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * This is where most of it happens.
 */
public class MixinTransformer implements ITransformer<ClassNode> {

	public static ClassPool cp = new ClassPool(true);

	public byte[] transform(String name, byte[] basicClass) {
		if (MixinManager.mixinTargetMap.containsKey(name)) {
			//This should not happen, just stop it from doing it anyway.
			if (MixinManager.transformedClasses.contains(name)) {
				MixinManager.logger.trace("Skipping mixin transformer as the transformer has already transformed this class");
				return basicClass;
			}
			//End support

			long start = System.currentTimeMillis();
			//makes a CtClass out of the byte array
			cp.insertClassPath(new ByteArrayClassPath(name, basicClass));
			CtClass target = null;
			try {
				target = cp.get(name);
			} catch (NotFoundException e) {
				e.printStackTrace();
				throw new RuntimeException("Failed to generate target infomation");
			}
			if (target.isFrozen()) {
				target.defrost();
			}

			List<String> mixins = MixinManager.mixinTargetMap.get(name);
			MixinManager.logger.info("Found " + mixins.size() + " mixins for " + name);
			for (String mixinClassName : mixins) {
				CtClass mixinClass = null;
				try {
					//loads the mixin class
					mixinClass = cp.get(mixinClassName);
				} catch (NotFoundException e) {
					throw new RuntimeException(e);
				}
				try {
					for (CtMethod method : mixinClass.getMethods()) {
						if (method.hasAnnotation(Rewrite.class)) {
							Rewrite annotation = (Rewrite) method.getAnnotation(Rewrite.class);
							//Copy's the mixin method to a new method targeting the target
							//This also renames the methord to contain the classname of the mixin
							CtMethod generatedMethod = CtNewMethod.copy(method, mixinClass.getName().replace(".", "$") + "$" + method.getName(), target, null);
							target.addMethod(generatedMethod);
							CtBehavior targetMethod = null;
							String targetName = annotation.target().isEmpty() ? method.getName() : annotation.target();
							boolean isConstructor = targetName.equals("<init>");
							if (!isConstructor) {
								for (CtMethod methodCandidate : target.getMethods()) {
									if (methodCandidate.getName().equals(targetName) && methodCandidate.getSignature().equals(method.getSignature())) {
										targetMethod = methodCandidate;
										break;
									}
								}
							} else {
								for (CtConstructor constructor : target.getConstructors()) {
									if (constructor.getSignature().equals(method.getSignature())) {
										targetMethod = constructor;
										break;
									}
								}
							}

							if (targetMethod == null) {
								MixinManager.logger.error("Could not find method to inject into");
								throw new RuntimeException("Could not find method " + targetName + " to inject into " + name);
							}
							//This generates the one line of code that calls the new method that was just injected above

							String src = null;
							String mCall = Modifier.isStatic(method.getModifiers()) ? "" : "this.";
							switch (annotation.returnBehaviour()) {
								case NONE:
									src = mCall + mixinClass.getName().replace(".", "$") + "$" + method.getName() + "($$);";
									break;
								case OBJECT_NONE_NULL:
									src = "Object mixinobj = " + mCall + generatedMethod.getName() + "($$);" + "if(mixinobj != null){return ($w)mixinobj;}";
									break;
								case BOOL_TRUE:
									if (!method.getReturnType().getName().equals("boolean")) {
										throw new RuntimeException(method.getName() + " does not return a boolean");
									}
									Validate.isTrue(targetMethod instanceof CtMethod);
									if (((CtMethod) targetMethod).getReturnType().getName().equals("boolean")) {
										src = "if(" + mCall + generatedMethod.getName() + "($$)" + "){return true;}";
										break;
									}
									src = "if(" + mCall + generatedMethod.getName() + "($$)" + "){return;}";
									break;
								default:
									src = mCall + mixinClass.getName().replace(".", "$") + "$" + method.getName() + "($$);";
									break;
							}

							//Adds it into the correct location
							switch (annotation.behavior()) {
								case START:
									targetMethod.insertBefore(src);
									break;
								case END:
									targetMethod.insertAfter(src);
									break;
								case REPLACE:
									targetMethod.setBody(src);
									break;
							}

						} else if (method.hasAnnotation(Inject.class)) {
							//Just copys and adds the method stright into the target class
							String methodName = method.getName();
							Inject inject = (Inject) method.getAnnotation(Inject.class);
							CtMethod generatedMethod = CtNewMethod.copy(method, methodName, target, null);

							try {
								//Removes the existing method if it exists
								String desc = Descriptor.ofMethod(generatedMethod.getReturnType(), generatedMethod.getParameterTypes());
								CtMethod existingMethod = target.getMethod(method.getName(), desc);
								if (existingMethod != null) {
									target.removeMethod(existingMethod);
								}
							} catch (NotFoundException e) {
								//Do nothing
							}

							target.addMethod(generatedMethod);
						}
					}
					for (CtField field : mixinClass.getFields()) {
						//Copy's the field over
						if (field.hasAnnotation(Inject.class)) {
							CtField generatedField = new CtField(field, target);
							target.addField(generatedField);
						}
					}
					//Adds all the interfaces from the mixin class to the target
					for (CtClass iface : mixinClass.getInterfaces()) {
						target.addInterface(iface);
					}
					for (CtConstructor constructor : mixinClass.getConstructors()) {
						if (constructor.hasAnnotation(Inject.class)) {
							CtConstructor generatedConstructor = CtNewConstructor.copy(constructor, target, null);
							target.addConstructor(generatedConstructor);
						}
					}
				} catch (NotFoundException | CannotCompileException | ClassNotFoundException e) {
					throw new RuntimeException(e);
				}
				MixinManager.logger.info("Successfully applied " + mixinClassName + " to " + name);
			}
			try {
				MixinManager.logger.info("Successfully applied " + mixins.size() + " mixins to " + name + " in " + (System.currentTimeMillis() - start) + "ms");
				MixinManager.transformedClasses.add(name);
				return target.toBytecode();
			} catch (IOException | CannotCompileException e) {
				throw new RuntimeException(e);
			}
		}

		return basicClass;
	}

	@Nonnull
	@Override
	public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
		//Sadly I think we need this, I know its not great, but it should work without breaking too much

		byte[] bytes = writeClassToBytes(input);
		System.out.println("Transforming " + input.name);
		input = readClassFromBytes(transform(input.name.replaceAll("/", "."), bytes));

		return input;
	}

	@Nonnull
	@Override
	public TransformerVoteResult castVote(ITransformerVotingContext context) {
		return TransformerVoteResult.YES;
	}

	@Nonnull
	@Override
	public Set<Target> targets() {
		Set<Target> targets = MixinManager.getAllTargets();
		MixinManager.logger.info("Found " + targets.size() + " possible mixin targets");
		return targets;
	}

	public static org.objectweb.asm.tree.ClassNode readClassFromBytes(byte[] bytes) {
		ClassNode classNode = new org.objectweb.asm.tree.ClassNode();
		ClassReader classReader = new ClassReader(bytes);
		classReader.accept(classNode, 0);
		return classNode;
	}

	public static byte[] writeClassToBytes(org.objectweb.asm.tree.ClassNode classNode) {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		classNode.accept(writer);
		return writer.toByteArray();
	}
}
