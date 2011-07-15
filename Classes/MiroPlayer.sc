MiroPlayer {
	var array, position, currentEnv, nextEnv, playing=false;
	var prepareNextCondition, playNextCondition, cleanupCondition, cleanupRoutine, prepRoutine;
	var routine;
	var gui, guiLabel, logWindow;
	
	*new {
		| array |
		^super.newCopyArgs( array )
	}
	
	play {
		var envArray;
		var prepareNextCondition, playNextCondition, cleanupCondition;
		playing = true;
		
		( "Starting playback of " + array.size + " items.").log;
		
		envArray = array.collect({
			| event |
			event.isArray.if(
				{ event.collect( MiroEnvironment.get(_) ) },
				{ [ MiroEnvironment.get( event ) ] } )
		});
		envArray[0].do( _.prepare );
		
		routine = Routine({
			inf.do({
				5.wait;
				envArray.do({
					| event, i |
					currentEnv = event;
					{
						guiLabel
							.string_( "current:" + if( currentEnv.notNil, {currentEnv.collect( _.name )}) + 
								" next:" + if( nextEnv.notNil, {nextEnv.collect( _.name )} ) );
					}.defer;
					nextEnv = envArray[i+1];
					position = i;
					
					event.collect( _.prepareNextCondition ).do({ |e| e.notNil.if({ e.wait }) });
					event.collect( _.prepare );

					( "Playing item " + i ).log;
					#prepareNextCondition, playNextCondition, cleanupCondition = event.collect( _.play ).flop;
					if( nextEnv.notNil, {
						prepRoutine = fork { 
							prepareNextCondition.do({ |e| e.notNil.if({ e.wait }) });
							( "Preparing item " + (i+1) ).log;
							nextEnv.do( _.prepare );
						};
					});
					cleanupRoutine = fork {
						cleanupCondition.do({ |e| e.notNil.if({ e.wait }) });
						( "Freeing item " + i ).log;
						event.do(_.free);
					};
					playNextCondition.do({ |e| e.notNil.if({ e.wait }) });
				});
				("Finished playback sequence, starting over").log;
			})
		}).play;
	}
	
	stop {
		routine.stop;
		cleanupRoutine.stop;
		prepRoutine.stop;
		routine = cleanupRoutine = prepRoutine = prepareNextCondition = playNextCondition = cleanupCondition = nil;
		array.do( _.free );
	}
	
	advanceGUI {
		gui = SCWindow( "MiroPlayer", Rect( 300,300, 300, 60));
		guiLabel = SCStaticText( gui, Rect( 5,5, 400, 15 ))
			.string_( "current:" + if( currentEnv.notNil, {currentEnv.collect( _.name )}) + 
				" next:" + if( nextEnv.notNil, {nextEnv.collect( _.name )}) );
		SCButton( gui, Rect( 5, 25, 140, 30 ) )
			.states_([["prepareNext", Color.white, Color.grey(0.4)]])
			.action_({ currentEnv.do( _.prepareNext ) });
		SCButton( gui, Rect( 150, 25, 140, 30 ) )
			.states_([["playNext", Color.white, Color.grey(0.4)]])
			.action_({ 
				currentEnv.do( _.playNext )
			});
		gui.front;
	}
}