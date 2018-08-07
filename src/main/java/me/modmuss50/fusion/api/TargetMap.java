package me.modmuss50.fusion.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Do not add this into your mixin class, it will be handled for you!
 */
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR })
@Retention(RetentionPolicy.RUNTIME)
public @interface TargetMap {

	/**
	 * This is most likey going to be the obof name, in the form of name()V
	 */
	String value();

}
