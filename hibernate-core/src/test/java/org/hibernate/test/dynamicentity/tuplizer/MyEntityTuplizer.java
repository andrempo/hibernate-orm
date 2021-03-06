package org.hibernate.test.dynamicentity.tuplizer;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.metamodel.spi.binding.EntityBinding;
import org.hibernate.property.Getter;
import org.hibernate.property.Setter;
import org.hibernate.proxy.ProxyFactory;
import org.hibernate.tuple.Instantiator;
import org.hibernate.tuple.entity.EntityMetamodel;
import org.hibernate.tuple.entity.PojoEntityTuplizer;

/**
 * @author Steve Ebersole
 */
public class MyEntityTuplizer extends PojoEntityTuplizer {

	public MyEntityTuplizer(EntityMetamodel entityMetamodel, PersistentClass mappedEntity) {
		super( entityMetamodel, mappedEntity );
	}

	public MyEntityTuplizer(EntityMetamodel entityMetamodel, EntityBinding entityBinding) {
		super( entityMetamodel, entityBinding);
	}

	protected Instantiator buildInstantiator(PersistentClass persistentClass) {
		return new MyEntityInstantiator( persistentClass.getEntityName() );
	}

	protected ProxyFactory buildProxyFactory(PersistentClass persistentClass, Getter idGetter, Setter idSetter) {
		// allows defining a custom proxy factory, which is responsible for
		// generating lazy proxies for a given entity.
		//
		// Here we simply use the default...
		return super.buildProxyFactory( persistentClass, idGetter, idSetter );
	}

	@Override
	protected Instantiator buildInstantiator(EntityBinding entityBinding) {
		return new MyEntityInstantiator( entityBinding.getEntityName() );
	}

	@Override
	protected ProxyFactory buildProxyFactory(EntityBinding entityBinding, Getter idGetter, Setter idSetter) {
		return super.buildProxyFactory( entityBinding, idGetter, idSetter );
	}

}
