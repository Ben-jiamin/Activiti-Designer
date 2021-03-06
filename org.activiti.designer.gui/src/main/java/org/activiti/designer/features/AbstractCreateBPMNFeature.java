 /**
 * 
 */
package org.activiti.designer.features;

import java.util.List;

import org.activiti.bpmn.model.Activity;
import org.activiti.bpmn.model.Artifact;
import org.activiti.bpmn.model.BaseElement;
import org.activiti.bpmn.model.BoundaryEvent;
import org.activiti.bpmn.model.CallActivity;
import org.activiti.bpmn.model.Event;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.FlowNode;
import org.activiti.bpmn.model.Gateway;
import org.activiti.bpmn.model.Lane;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.bpmn.model.SubProcess;
import org.activiti.bpmn.model.Task;
import org.activiti.designer.util.eclipse.ActivitiUiUtil;
import org.activiti.designer.util.editor.Bpmn2MemoryModel;
import org.activiti.designer.util.editor.ModelHandler;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.ICreateContext;
import org.eclipse.graphiti.features.context.impl.CreateConnectionContext;
import org.eclipse.graphiti.features.context.impl.CreateContext;
import org.eclipse.graphiti.features.impl.AbstractCreateFeature;
import org.eclipse.graphiti.mm.pictograms.Anchor;
import org.eclipse.graphiti.mm.pictograms.AnchorContainer;
import org.eclipse.graphiti.mm.pictograms.ChopboxAnchor;
import org.eclipse.graphiti.mm.pictograms.Connection;
import org.eclipse.graphiti.mm.pictograms.ContainerShape;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.services.Graphiti;

/**
 * @author Tiese Barrell
 * @version 2
 * @since 0.5.0
 * 
 */
public abstract class AbstractCreateBPMNFeature extends AbstractCreateFeature {

	private static final String CONNECTION_ATTRIBUTE = "org.activiti.designer.connectionContext";
	
  public AbstractCreateBPMNFeature(IFeatureProvider fp, String name, String description) {
    super(fp, name, description);
  }

  protected abstract String getFeatureIdKey();

  protected String getNextId(BaseElement element) {
    return ActivitiUiUtil.getNextId(element.getClass(), getFeatureIdKey(), getDiagram());
  }
  
  protected String getNextId(BaseElement element, String featureIdKey) {
    return ActivitiUiUtil.getNextId(element.getClass(), featureIdKey, getDiagram());
  }
  
  public boolean canCreate(ICreateContext context) {
    Object parentObject = getBusinessObjectForPictogramElement(context.getTargetContainer());
    return (context.getTargetContainer() instanceof Diagram || 
            parentObject instanceof SubProcess || parentObject instanceof Lane);
  }
  
  private void addFlowNodeOrArtifact(final BaseElement baseElement, final BaseElement container) {
    
    if (container instanceof Process) {
      final Process process = (Process) container;
      
      if (baseElement instanceof FlowElement) {
        process.addFlowElement((FlowElement) baseElement);
      } else if (baseElement instanceof Artifact) {
        process.addArtifact((Artifact) baseElement);
      } else {
        throw new IllegalArgumentException("BaseElement must be FlowElement or Artifact.");
      }
      
    } else if (container instanceof SubProcess) {
      final SubProcess subProcess = (SubProcess) container;
      
      if (baseElement instanceof FlowElement) {
        subProcess.addFlowElement((FlowElement) baseElement);
      } else if (baseElement instanceof Artifact) {
        subProcess.addArtifact((Artifact) baseElement);
      } else {
        throw new IllegalArgumentException("BaseElement must be FlowElement or Artifact.");
      }
    } else {
      throw new IllegalArgumentException("Container must be Process or SubProcess.");
    }
  }
  
  /**
   * Adds the given base element to the context. At first, a new ID is generated for the new object.
   * Depending on the type of element, it is added as artifact or flow element.
   * 
   * @param context the context to add it
   * @param baseElement the base element to add
   */
  protected void addObjectToContainer(ICreateContext context, BaseElement baseElement) {
    baseElement.setId(getNextId(baseElement));
    final ContainerShape targetContainer = context.getTargetContainer();
    addBaseElementToContainer(targetContainer, baseElement);
    addGraphicalContent(context, baseElement);
  }
  
  protected void addBaseElementToContainer(ContainerShape targetContainer, BaseElement baseElement) {
    if (targetContainer instanceof Diagram) {
      final Bpmn2MemoryModel model = ModelHandler.getModel(EcoreUtil.getURI(getDiagram()));
      if (model.getBpmnModel().getMainProcess() == null) {
        model.addMainProcess();
      }
      addFlowNodeOrArtifact(baseElement, model.getBpmnModel().getMainProcess());
    }
    else {
      // find the parent object
      final Object parent = getBusinessObjectForPictogramElement(targetContainer);
      
      if (parent instanceof SubProcess) {
        boolean addToSubProcess = true;
        if (baseElement instanceof BoundaryEvent) {
          BoundaryEvent boundaryEvent = (BoundaryEvent) baseElement;
          if (boundaryEvent.getAttachedToRef() != null && boundaryEvent.getAttachedToRef().getId().equals(((SubProcess) parent).getId())) {
            addToSubProcess = false;
          }
        }
        if (addToSubProcess == false) {
          ContainerShape parentContainer = targetContainer.getContainer();
          addBaseElementToContainer(parentContainer, baseElement);
        } else {
          addFlowNodeOrArtifact(baseElement, (SubProcess) parent);
        }
        
      } else if (parent instanceof Lane) {
        final Lane lane = (Lane) parent;
        
        // for flow elements, the lane gets informed about the flow elements Id 
        if (baseElement instanceof FlowElement)
        {
          final FlowElement flowElement = (FlowElement) baseElement;
          lane.getFlowReferences().add(flowElement.getId());
        }
  
        addFlowNodeOrArtifact(baseElement, lane.getParentProcess());
        
      } else if (parent instanceof Activity) {
        ContainerShape parentContainer = targetContainer.getContainer();
        addBaseElementToContainer(parentContainer, baseElement);
      }
    }
  }
  
  protected void addObjectToContainer(ICreateContext context, FlowNode flowNode, String name) {
    setName(name, flowNode, context);
    addObjectToContainer(context, flowNode);
  }
  
  @SuppressWarnings("unchecked")
  protected void addGraphicalContent(ICreateContext context, BaseElement targetElement) {
  	setLocation(targetElement, (CreateContext) context);
		PictogramElement element = addGraphicalRepresentation(context, targetElement);
		createConnectionIfNeeded(element, context);
		
		Anchor elementAnchor = null;
    EList<Anchor> anchorList = ((ContainerShape) element).getAnchors();
    for (Anchor anchor : anchorList) {
      if(anchor instanceof ChopboxAnchor) {
      	elementAnchor = anchor;
        break;
      }
    }
		
		if(context.getProperty("org.activiti.designer.changetype.sourceflows") != null) {
  		List<SequenceFlow> sourceFlows = (List<SequenceFlow>) context.getProperty("org.activiti.designer.changetype.sourceflows");
  		for (SequenceFlow sourceFlow : sourceFlows) {
  			sourceFlow.setSourceRef(targetElement.getId());
  			if (targetElement instanceof FlowNode) {
  			  ((FlowNode) targetElement).getOutgoingFlows().add(sourceFlow);
  			}
  			Connection connection = (Connection) getFeatureProvider().getPictogramElementForBusinessObject(sourceFlow);
  			connection.setStart(elementAnchor);
      	elementAnchor.getOutgoingConnections().add(connection);
      }
  		List<SequenceFlow> targetFlows = (List<SequenceFlow>) context.getProperty("org.activiti.designer.changetype.targetflows");
  		for (SequenceFlow targetFlow : targetFlows) {
  			targetFlow.setTargetRef(targetElement.getId());
  			if (targetElement instanceof FlowNode) {
          ((FlowNode) targetElement).getIncomingFlows().add(targetFlow);
        }
  			Connection connection = (Connection) getFeatureProvider().getPictogramElementForBusinessObject(targetFlow);
  			connection.setEnd(elementAnchor);
      	elementAnchor.getIncomingConnections().add(connection);
      }
		}
  }
  
  protected void setName(String defaultName, FlowElement targetElement, ICreateContext context) {
  	if(context.getProperty("org.activiti.designer.changetype.name") != null) {
  		targetElement.setName(context.getProperty("org.activiti.designer.changetype.name").toString());
  	} else {
  		targetElement.setName(defaultName);
  	}
  }
  
  private void setLocation(BaseElement targetElement, CreateContext context) {
  	if(context.getProperty(CONNECTION_ATTRIBUTE) != null) {
  		
  		CreateConnectionContext connectionContext = (CreateConnectionContext) 
					context.getProperty(CONNECTION_ATTRIBUTE);
  		PictogramElement sourceElement = connectionContext.getSourcePictogramElement();
  		Object sourceObject = getBusinessObjectForPictogramElement(sourceElement);
  		if(sourceObject instanceof Event && (targetElement instanceof Task || targetElement instanceof CallActivity)) {
  			context.setLocation(sourceElement.getGraphicsAlgorithm().getX() + 80, 
  					sourceElement.getGraphicsAlgorithm().getY() - 10);
  		
  		} else if(sourceObject instanceof Event && targetElement instanceof Gateway) {
  			context.setLocation(sourceElement.getGraphicsAlgorithm().getX() + 80, 
  					sourceElement.getGraphicsAlgorithm().getY() - 3);
  			
  		} else if(sourceObject instanceof Gateway && targetElement instanceof Event) {
  			context.setLocation(sourceElement.getGraphicsAlgorithm().getX() + 85, 
  					sourceElement.getGraphicsAlgorithm().getY() + 3);
  		
  		} else if(sourceObject instanceof Gateway && (targetElement instanceof Task || targetElement instanceof CallActivity)) {
  			context.setLocation(sourceElement.getGraphicsAlgorithm().getX() + 85, 
  					sourceElement.getGraphicsAlgorithm().getY() - 7);
  		
  		} else if((sourceObject instanceof Task || sourceObject instanceof CallActivity) && targetElement instanceof Gateway) {
  			context.setLocation(sourceElement.getGraphicsAlgorithm().getX() + 160, 
  					sourceElement.getGraphicsAlgorithm().getY() + 7);
  		
  		} else if((sourceObject instanceof Task || sourceObject instanceof CallActivity) && targetElement instanceof Event) {
  			context.setLocation(sourceElement.getGraphicsAlgorithm().getX() + 160, 
  					sourceElement.getGraphicsAlgorithm().getY() + 10);
  		
  		} else if((sourceObject instanceof Task || sourceObject instanceof CallActivity) && (targetElement instanceof Task || targetElement instanceof CallActivity)) {
  			context.setLocation(sourceElement.getGraphicsAlgorithm().getX() + 160, 
  					sourceElement.getGraphicsAlgorithm().getY());
  		}
  	}
  }

  private void createConnectionIfNeeded(PictogramElement element, ICreateContext context) {
  	if(context.getProperty(CONNECTION_ATTRIBUTE) != null) {
  		
			CreateConnectionContext connectionContext = (CreateConnectionContext) 
					context.getProperty(CONNECTION_ATTRIBUTE);
			connectionContext.setTargetPictogramElement(element);
			connectionContext.setTargetAnchor(Graphiti.getPeService().getChopboxAnchor((AnchorContainer) element));
			CreateSequenceFlowFeature sequenceFeature = new CreateSequenceFlowFeature(getFeatureProvider());
			sequenceFeature.create(connectionContext);
		}
  }

}
