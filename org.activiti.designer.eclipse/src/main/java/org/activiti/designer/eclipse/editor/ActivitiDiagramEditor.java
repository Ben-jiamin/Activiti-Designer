package org.activiti.designer.eclipse.editor;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.activiti.designer.bpmn2.model.BoundaryEvent;
import org.activiti.designer.bpmn2.model.FlowElement;
import org.activiti.designer.bpmn2.model.FlowNode;
import org.activiti.designer.bpmn2.model.SequenceFlow;
import org.activiti.designer.eclipse.bpmn.BpmnParser;
import org.activiti.designer.eclipse.bpmn.SequenceFlowModel;
import org.activiti.designer.eclipse.ui.ActivitiEditorContextMenuProvider;
import org.activiti.designer.export.bpmn20.export.BPMN20ExportMarshaller;
import org.activiti.designer.util.editor.Bpmn2MemoryModel;
import org.activiti.designer.util.editor.GraphicInfo;
import org.activiti.designer.util.editor.ModelHandler;
import org.apache.commons.lang.StringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.draw2d.IFigure;
import org.eclipse.emf.common.command.BasicCommandStack;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.gef.ContextMenuProvider;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.LayerConstants;
import org.eclipse.gef.editparts.LayerManager;
import org.eclipse.gef.editparts.ScalableFreeformRootEditPart;
import org.eclipse.graphiti.dt.IDiagramTypeProvider;
import org.eclipse.graphiti.features.IAddFeature;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.impl.AddConnectionContext;
import org.eclipse.graphiti.features.context.impl.AddContext;
import org.eclipse.graphiti.features.context.impl.AreaContext;
import org.eclipse.graphiti.mm.pictograms.Anchor;
import org.eclipse.graphiti.mm.pictograms.ChopboxAnchor;
import org.eclipse.graphiti.mm.pictograms.ContainerShape;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.ui.editor.DiagramEditor;
import org.eclipse.graphiti.ui.editor.DiagramEditorInput;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.PartInitException;


public class ActivitiDiagramEditor extends DiagramEditor {

	public final static String ID = "org.activiti.designer.diagmrameditor"; //$NON-NLS-1$
	private static GraphicalViewer activeGraphicalViewer;

	private IFile modelFile;
	private IFile diagramFile;
	
	private BpmnParser parser;
	
	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		
		try {
			if (input instanceof IFileEditorInput) {
				modelFile = ((IFileEditorInput) input).getFile();
				input = createNewDiagramEditorInput();
			} else if (input instanceof DiagramEditorInput) {
				getModelPathFromInput((DiagramEditorInput) input);
				input = createNewDiagramEditorInput();
			}

		} catch (CoreException e) {
			e.printStackTrace();
		}
		
		super.init(site, input);
	}
	
	private void getModelPathFromInput(DiagramEditorInput input) {
		URI uri = input.getUri();
		String uriString = uri.trimFragment().toPlatformString(true);
		modelFile = Bpmn2DiagramCreator.getModelFile(new Path(uriString));
	}

	/**
	 * Beware, creates a new input and changes this editor!
	 */
	private DiagramEditorInput createNewDiagramEditorInput() throws CoreException {
		IPath fullPath = modelFile.getFullPath();
		
		IFolder folder = Bpmn2DiagramCreator.getTempFolder(fullPath);
		diagramFile = Bpmn2DiagramCreator.getTempFile(fullPath, folder);

		// Create new temporary diagram file
		Bpmn2DiagramCreator creator = new Bpmn2DiagramCreator();
		creator.setDiagramFile(diagramFile);

		DiagramEditorInput input = creator.createDiagram(false);
		
		return input;
	}

	@Override
	public void doSave(IProgressMonitor monitor) {
		IDiagramTypeProvider diagramTypeProvider = this.getDiagramTypeProvider();
		
		try {
			
			String diagramFileString = modelFile.getLocationURI().getRawPath();
			
			BPMN20ExportMarshaller marshaller = new BPMN20ExportMarshaller();
			marshaller.marshallDiagram(ModelHandler.getModel(EcoreUtil.getURI(getDiagramTypeProvider().getDiagram())), 
					diagramFileString, diagramTypeProvider.getFeatureProvider());
			
			modelFile.refreshLocal(IResource.DEPTH_INFINITE, null);
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		((BasicCommandStack) getEditingDomain().getCommandStack()).saveIsDone();
		updateDirtyState();
	}

	@Override
	protected void setInput(IEditorInput input) {
		super.setInput(input);
		Bpmn2MemoryModel model = new Bpmn2MemoryModel(getDiagramTypeProvider().getFeatureProvider(), "process1");
		ModelHandler.addModel(EcoreUtil.getURI(getDiagramTypeProvider().getDiagram()), model);
		
		File bpmnFile = new File(modelFile.getLocationURI().getRawPath());
    try {
    	if(bpmnFile.exists() == false) {
  			bpmnFile.createNewFile();
  			modelFile.refreshLocal(IResource.DEPTH_INFINITE, null);
  		}
    	FileInputStream fileStream = new FileInputStream(bpmnFile);
      XMLInputFactory xif = XMLInputFactory.newInstance();
      InputStreamReader in = new InputStreamReader(fileStream, "UTF-8");
      XMLStreamReader xtr = xif.createXMLStreamReader(in);
      parser = new BpmnParser();
      parser.parseBpmn(xtr, model);
      
    } catch(Exception e) {
      e.printStackTrace();
    }
		
		BasicCommandStack basicCommandStack = (BasicCommandStack) getEditingDomain().getCommandStack();

		if (input instanceof DiagramEditorInput) {

			basicCommandStack.execute(new RecordingCommand(getEditingDomain()) {

				@Override
				protected void doExecute() {
					importDiagram();
				}
			});
		}
		basicCommandStack.saveIsDone();
		basicCommandStack.flush();
	}
	
	private void importDiagram() {
		final Diagram diagram = getDiagramTypeProvider().getDiagram();
		final IFeatureProvider featureProvider = getDiagramTypeProvider().getFeatureProvider();
		diagram.setActive(true);
		
		getEditingDomain().getCommandStack().execute(new RecordingCommand(getEditingDomain()) {
			@Override
			protected void doExecute() {
				
				Bpmn2MemoryModel model = ModelHandler.getModel(EcoreUtil.getURI(getDiagramTypeProvider().getDiagram()));
				
				for (FlowElement flowElement : model.getProcess().getFlowElements()) {
					
					if(flowElement instanceof BoundaryEvent == true) continue;
					
					AddContext context = new AddContext(new AreaContext(), flowElement);
					IAddFeature addFeature = featureProvider.getAddFeature(context);
					
					if (addFeature == null) {
						System.out.println("Element not supported: " + flowElement);
						return;
					}
					
					GraphicInfo graphicInfo = model.getGraphicInfo(flowElement.getId());
					if(graphicInfo != null) {

						context.setNewObject(flowElement);
						context.setSize(graphicInfo.width, graphicInfo.height);
						context.setTargetContainer(diagram);
						context.setLocation(graphicInfo.x, graphicInfo.y);
	
						if (addFeature.canAdd(context)) {
							PictogramElement newContainer = addFeature.add(context);
							featureProvider.link(newContainer, new Object[] { flowElement });
						}
					}
        }
				
				for (FlowElement flowElement : model.getProcess().getFlowElements()) {
					
					if(flowElement instanceof BoundaryEvent == false) continue;
					
					AddContext context = new AddContext(new AreaContext(), flowElement);
					IAddFeature addFeature = featureProvider.getAddFeature(context);
					
					if (addFeature == null) {
						System.out.println("Element not supported: " + flowElement);
						return;
					}
					
					GraphicInfo graphicInfo = model.getGraphicInfo(flowElement.getId());
					if(graphicInfo != null) {

						context.setNewObject(flowElement);
						context.setSize(graphicInfo.width, graphicInfo.height);
						
						BoundaryEvent boundaryEvent = (BoundaryEvent) flowElement;
						if(boundaryEvent.getAttachedToRef() != null) {
							ContainerShape container = (ContainerShape) featureProvider.getPictogramElementForBusinessObject(
									boundaryEvent.getAttachedToRef());
							
							if(container != null) {
							
								context.setTargetContainer(container);
								context.setLocation(graphicInfo.x, graphicInfo.y);
			
								if (addFeature.canAdd(context)) {
									PictogramElement newContainer = addFeature.add(context);
									featureProvider.link(newContainer, new Object[] { flowElement });
								}
							}
						}
					}
				}
				
				drawSequenceFlows();
			}
		});
	}
	
	private void drawSequenceFlows() {
		Bpmn2MemoryModel model = ModelHandler.getModel(EcoreUtil.getURI(getDiagramTypeProvider().getDiagram()));
    int sequenceCounter = 1;
    for(SequenceFlowModel sequenceFlowModel : parser.sequenceFlowList) {
      SequenceFlow sequenceFlow = new SequenceFlow();
      if(StringUtils.isEmpty(sequenceFlowModel.id) || sequenceFlowModel.id.matches("sid-\\w{4,12}-\\w{4,12}-\\w{4,12}-\\w{4,12}-\\w{4,12}")) {
      	sequenceFlow.setId("flow" + sequenceCounter);
      	sequenceCounter++;
      } else {
      	sequenceFlow.setId(sequenceFlowModel.id);
      }
      sequenceFlow.setSourceRef(getFlowNode(sequenceFlowModel.sourceRef));
      sequenceFlow.setTargetRef(getFlowNode(sequenceFlowModel.targetRef));
      if(sequenceFlow.getSourceRef() == null || sequenceFlow.getSourceRef().getId() == null || 
              sequenceFlow.getTargetRef() == null || sequenceFlow.getTargetRef().getId() == null) continue;
      if(sequenceFlowModel.conditionExpression != null) {
        sequenceFlow.setConditionExpression(sequenceFlowModel.conditionExpression);
      }
      if(sequenceFlowModel.listenerList.size() > 0) {
        sequenceFlow.getExecutionListeners().addAll(sequenceFlowModel.listenerList);
      }
      
      model.addFlowElement(sequenceFlow);
      
      /*SubProcess subProcess = subProcessContains(sequenceFlow.getSourceRef(), subProcessList);
      if(subProcess != null) {
        ILinkService linkService = Graphiti.getLinkService();
        List<PictogramElement> pictoList = linkService.getPictogramElements(diagram, subProcess);
        if(pictoList != null && pictoList.size() > 0) {
          parent = (ContainerShape) pictoList.get(0);
          subProcess.getFlowElements().add(sequenceFlow);
        }
      }*/
      
      sequenceFlow.getSourceRef().getOutgoing().add(sequenceFlow);
      sequenceFlow.getTargetRef().getIncoming().add(sequenceFlow);
      
      Anchor sourceAnchor = null;
      Anchor targetAnchor = null;
      ContainerShape sourceShape = (ContainerShape) getDiagramTypeProvider().getFeatureProvider().getPictogramElementForBusinessObject(sequenceFlow.getSourceRef());
      EList<Anchor> anchorList = sourceShape.getAnchors();
      for (Anchor anchor : anchorList) {
        if(anchor instanceof ChopboxAnchor) {
          sourceAnchor = anchor;
          break;
        }
      }
      
      ContainerShape targetShape = (ContainerShape) getDiagramTypeProvider().getFeatureProvider().getPictogramElementForBusinessObject(sequenceFlow.getTargetRef());
      anchorList = targetShape.getAnchors();
      for (Anchor anchor : anchorList) {
        if(anchor instanceof ChopboxAnchor) {
          targetAnchor = anchor;
          break;
        }
      }
      
      AddConnectionContext addContext = new AddConnectionContext(sourceAnchor, targetAnchor);
      
      List<GraphicInfo> bendpointList = new ArrayList<GraphicInfo>();
      for (String sequenceGraphElement : parser.flowLocationMap.keySet()) {
	      if(sequenceFlowModel.id.equalsIgnoreCase(sequenceGraphElement)) {
	      	List<GraphicInfo> pointList = parser.flowLocationMap.get(sequenceGraphElement);
	      	if(pointList.size() > 2) {
	      		for(int i = 1; i < pointList.size() - 1; i++) {
	      			bendpointList.add(pointList.get(i));
	      		}
	      	}
	      }
      }
    
      addContext.putProperty("org.activiti.designer.bendpoints", bendpointList);
      
      addContext.setNewObject(sequenceFlow);
      getDiagramTypeProvider().getFeatureProvider().addIfPossible(addContext);
    }
  }
	
	private FlowNode getFlowNode(String elementid) {
    FlowNode flowNode = null;
    for(FlowElement flowElement : ModelHandler.getModel(EcoreUtil.getURI(getDiagramTypeProvider().getDiagram())).getProcess().getFlowElements()) {
      if(flowElement.getId().equalsIgnoreCase(elementid)) {
        flowNode = (FlowNode) flowElement;
      }
    }
    return flowNode;
  }
	
	public IFile getModelFile() {
		return modelFile;
	}
	
	@Override
	public void createPartControl(Composite parent) {
	  super.createPartControl(parent);
	  GraphicalViewer graphicalViewer = (GraphicalViewer) getAdapter(GraphicalViewer.class);
    if (graphicalViewer != null && graphicalViewer.getEditPartRegistry() != null) {
      ScalableFreeformRootEditPart rootEditPart = (ScalableFreeformRootEditPart) graphicalViewer.getEditPartRegistry().get(LayerManager.ID);
      IFigure gridFigure = ((LayerManager) rootEditPart).getLayer(LayerConstants.GRID_LAYER);
      gridFigure.setVisible(false);
    }
	}

	/*@Override
	protected DefaultPersistencyBehavior createPersistencyBehavior() {
	  return new ActivitiDiagramEditorPersistenceBehavior(this);
	}*/

	@Override
	protected ContextMenuProvider createContextMenuProvider() {
		return new ActivitiEditorContextMenuProvider(getGraphicalViewer(),
				getActionRegistry(), getDiagramTypeProvider());
	}

	public static GraphicalViewer getActiveGraphicalViewer() {
		return activeGraphicalViewer;
	}
	
	@Override
	public void dispose() {
		super.dispose();
		Bpmn2DiagramCreator.dispose(diagramFile);
	}
}
