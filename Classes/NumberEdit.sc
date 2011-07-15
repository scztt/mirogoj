NumberEdit {
	var parent, rect, labelText, labelWidth,
		view, editView, labelRect, valueRect,
		spec, <value, <>formatFunc, <spec,
		<fontSize=12, labelFont, valueFont, labelColor, valueColor, editingColor,
		clickStart, valueStart, valueString,
		<>changedAction;
		
	
	*new {
		| parent, rect, labelText, labelWidth |
		^super.new.init( parent, rect, labelText, labelWidth );
	}
	
	init {
		| aParent, aRect, aLabelText, aLabelWidth |
		parent = aParent;
		rect = aRect ? Rect( 0,0, 200, 20);
		labelText = aLabelText;
		labelWidth = (aLabelWidth ? 
			aLabelText.isNil.if({ 0 }, { rect.bounds.width*0.65 })).min( rect.width );
		labelRect = Rect( 1, 1, labelWidth, rect.height-2 );
		valueRect = Rect( labelWidth+1, 1, labelWidth, rect.height-2 );
		
		view = SCUserView( parent, rect )
			.relativeOrigin_(true)
			.drawFunc_({ |view| this.draw( view ) })
			.mouseDownAction_({ |view, x, y| this.mouseDownAction( view, x, y ) })
			.mouseMoveAction_({ |view, x, y| this.mouseMoveAction( view, x, y ) })			.mouseUpAction_({ |view, x, y| this.mouseUpAction( view, x, y ) })
			.canFocus_( true )
			.focusColor_( Color.clear )
			.keyDownAction_({
				| ...args |
				this.keyAction( *args )
			});
		
		spec = [-inf, inf, 'lin', 0.01].asSpec;

		labelFont = Font.new( "Helvetica", 11 );
		labelColor = Color.black;
		valueFont = Font.new( "Helvetica", 11 );
		valueColor = Color.blue;
		editingColor = Color.green(0.5);

		editView = SCTextField( parent, valueRect )
			.visible_(false)
			.font_( valueFont )
			.boxColor_( Color.grey( 1, 0.5 ) )
			.focusColor_( Color.clear )
			.action_({ 
				|view|
				try({ 
					var newVal = view.string.interpret;
					if( newVal.isNumber, { this.value=newVal });
				});
				view.visible = false;
			});
			
		this.dependants();
	}
	
	fontSize_{
		| size |
		fontSize = size;
		labelFont = Font.new( "Helvetica", fontSize );
		valueFont = Font.new( "Helvetica", fontSize );
		view.refresh;
	}
	
	spec_{
		| val |
		spec = val;
		value = value;
		view.refresh;
	}
	
	value_{
		| val |
		value = spec.constrain( val );
		valueString = this.format();
		view.refresh();
	}
	
	prValue_{
		|val|
		value = spec.constrain( val );
		valueString = this.format();
		this.changed();
		this.hasChanged();
		view.refresh();
	}
	
	format {
		var str;
		if( formatFunc.notNil, {
			^formatFunc.value()
		},{
			str = value.asString();
			^(value.asString()[0..7] + spec.units) 
		})
	}
	
	draw {
		| view |
		if( labelText.notNil, {
			labelText.drawRightJustIn( labelRect, labelFont, labelColor ); 
		});
		if( valueString.notNil, {
			valueString.drawLeftJustIn( valueRect, valueFont, 
				if( view.hasFocus, {editingColor}, {valueColor} ) );
		},{
			valueStart.asString().drawLeftJustIn( valueRect, valueFont, editingColor );
		});
	}

	mouseDownAction {
		| view, x, y |
		clickStart = x@y;
		valueStart = value;
	}
	
	mouseMoveAction {
		| view, x, y |
		this.prValue_( valueStart + 
			((clickStart.y - y).floor * spec.range.min(1000)/400) +
			((x - clickStart.x).floor * spec.range.min(1000)/400)
		);
	}
	
	mouseUpAction {
		| view, x, y |
		if( (x@y-clickStart).rho < 3, {
			view.focus( true );
			{
				view.refresh();
				valueString = nil;
			}.defer();
		},{
			view.focus( false );
			view.refresh();
		})
	}
	
	keyAction {
		| view, char, modifiers, unicode |
		var newValue;
		if( unicode == 27,  {
			valueString = this.format;
			view.focus( false );
			view.refresh();
			^this
		});
		if ((char == 3.asAscii) || (char == $\r) || (char == $\n), { // enter key
			if (valueString.notNil,{ // no error on repeated enter
				try({
					this.prValue = valueString.interpret;
				});
				valueString = this.format;
			});
			view.focus( false );
			view.refresh();
			^this
		});
		if (char == 127.asAscii, { // delete key
			valueString = nil;
			view.refresh();
			^this
		});
		if (char.isDecDigit || "+-.eE".includes(char), {
			if (valueString.isNil, { 
				valueString = String.new;
			});
			valueString = valueString.add(char);
			view.refresh();
			^this
		});
		^nil		// bubble if it's an invalid key	
	}
	
	hasChanged {
		if( changedAction.notNil, {
			changedAction.value( this )
		})
	}
	
	update {
	 	| changed, changer |
	 	if( changer != this, {
			this.value = changed.value
		})
	}	
}